-- MySQL 8.0 项目空库初始化脚本；平台基础表 DDL 直接导出自当前项目数据库。
-- 在预先创建的空数据库中执行：mysql --default-character-set=utf8mb4 数据库名 < docs/sql/mysql.sql
-- 本文件是独立导入脚本，不是 Flyway B 迁移；不要在已执行 Flyway 迁移的库中重复导入。
-- 结构含平台基础表、Flowable 引擎表、索引和约束；不含当前项目的 biz_* 动态表。
-- 数据仅保留系统元数据、内置配置、菜单权限及超级管理员，不含业务或演示记录。
-- 超级管理员 admin 初始禁用；必须按项目 Bootstrap 流程设置 WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD 激活。
-- 签名密钥在每次导入时随机生成，不能使用来源数据库中的固定密钥。


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_APP_APPDEF` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `VERSION_` int NOT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源文件名，定位部署包内的模型内容',
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_APP_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`),
  KEY `ACT_IDX_APP_DEF_DPLY` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_APP_DEF_DPLY` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_APP_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 应用模型应用定义表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_APP_DEPLOYMENT` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL COMMENT '部署时间，用于模型版本管理和历史追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 应用模型部署包表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_APP_DEPLOYMENT_RESOURCE` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_BYTES_` longblob COMMENT '资源二进制内容，保存部署时上传的模型文件',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_APP_RSRC_DPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_APP_RSRC_DPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_APP_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 应用模型部署包资源文件表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_CASEDEF` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `VERSION_` int NOT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源文件名，定位部署包内的模型内容',
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `HAS_GRAPHICAL_NOTATION_` bit(1) DEFAULT NULL COMMENT 'HAS_GRAPHICAL_NOTATION_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `DGRM_RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DGRM_RESOURCE_NAME_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `HAS_START_FORM_KEY_` bit(1) DEFAULT NULL COMMENT 'HAS_START_FORM_KEY_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_CASE_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`),
  KEY `ACT_IDX_CASE_DEF_DPLY` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_CASE_DEF_DPLY` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_CMMN_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理案例定义表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_DEPLOYMENT` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL COMMENT '部署时间，用于模型版本管理和历史追踪',
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理部署包表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_DEPLOYMENT_RESOURCE` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_BYTES_` longblob COMMENT '资源二进制内容，保存部署时上传的模型文件',
  `GENERATED_` bit(1) DEFAULT NULL COMMENT 'GENERATED_ 引擎属性，供 Flowable 案例管理部署包资源文件表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_CMMN_RSRC_DPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_CMMN_RSRC_DPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_CMMN_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理部署包资源文件表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_HI_CASE_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `BUSINESS_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务键，供应用按业务记录查找流程实例',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `PARENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT 标识，关联 Flowable 引擎中的对应记录',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `STATE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎状态，决定实例或作业后续可执行操作',
  `START_TIME_` datetime(3) DEFAULT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `END_TIME_` datetime(3) DEFAULT NULL COMMENT '结束时间，用于判定实例完成和历史统计',
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_USER 标识，关联 Flowable 引擎中的对应记录',
  `CALLBACK_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CALLBACK 标识，关联 Flowable 引擎中的对应记录',
  `CALLBACK_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回调类型，决定异步结果处理方式',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REFERENCE 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用目标类型，供引擎解析关联对象',
  `LAST_REACTIVATION_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_REACTIVATION 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_REACTIVATION_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LAST_REACTIVATION_USER 标识，关联 Flowable 引擎中的对应记录',
  `BUSINESS_STATUS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务状态，供引擎和应用同步实例进度',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_CASE_INST_END` (`END_TIME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理HI案例实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_HI_MIL_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `TIME_STAMP_` datetime(3) DEFAULT NULL COMMENT '事件时间戳，用于排序和审计追踪',
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CASE_INST 标识，关联 Flowable 引擎中的对应记录',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理HI里程碑实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_HI_PLAN_ITEM_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `STATE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎状态，决定实例或作业后续可执行操作',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_INST 标识，关联 Flowable 引擎中的对应记录',
  `STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'STAGE_INST 标识，关联 Flowable 引擎中的对应记录',
  `IS_STAGE_` bit(1) DEFAULT NULL COMMENT 'IS_STAGE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `ITEM_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ITEM_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `ITEM_DEFINITION_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ITEM_DEFINITION_TYPE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `LAST_AVAILABLE_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_AVAILABLE 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_ENABLED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_ENABLED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_DISABLED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_DISABLED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_STARTED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_STARTED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_SUSPENDED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_SUSPENDED 时间，供 Flowable 引擎排序和历史追踪',
  `COMPLETED_TIME_` datetime(3) DEFAULT NULL COMMENT 'COMPLETED 时间，供 Flowable 引擎排序和历史追踪',
  `OCCURRED_TIME_` datetime(3) DEFAULT NULL COMMENT 'OCCURRED 时间，供 Flowable 引擎排序和历史追踪',
  `TERMINATED_TIME_` datetime(3) DEFAULT NULL COMMENT 'TERMINATED 时间，供 Flowable 引擎排序和历史追踪',
  `EXIT_TIME_` datetime(3) DEFAULT NULL COMMENT 'EXIT 时间，供 Flowable 引擎排序和历史追踪',
  `ENDED_TIME_` datetime(3) DEFAULT NULL COMMENT 'ENDED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪',
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_USER 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REFERENCE 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用目标类型，供引擎解析关联对象',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `ENTRY_CRITERION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ENTRY_CRITERION 标识，关联 Flowable 引擎中的对应记录',
  `EXIT_CRITERION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXIT_CRITERION 标识，关联 Flowable 引擎中的对应记录',
  `SHOW_IN_OVERVIEW_` bit(1) DEFAULT NULL COMMENT 'SHOW_IN_OVERVIEW_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `EXTRA_VALUE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXTRA_VALUE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DERIVED_CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DERIVED_CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `LAST_UNAVAILABLE_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_UNAVAILABLE 时间，供 Flowable 引擎排序和历史追踪',
  `ASSIGNEE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办理人标识，供任务分派和待办查询',
  `COMPLETED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完成人标识，供历史记录追溯责任人',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_PLAN_ITEM_INST_CASE` (`CASE_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_RU_CASE_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `BUSINESS_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务键，供应用按业务记录查找流程实例',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `PARENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT 标识，关联 Flowable 引擎中的对应记录',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `STATE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎状态，决定实例或作业后续可执行操作',
  `START_TIME_` datetime(3) DEFAULT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_USER 标识，关联 Flowable 引擎中的对应记录',
  `CALLBACK_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CALLBACK 标识，关联 Flowable 引擎中的对应记录',
  `CALLBACK_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回调类型，决定异步结果处理方式',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `LOCK_TIME_` datetime(3) DEFAULT NULL COMMENT 'LOCK 时间，供 Flowable 引擎排序和历史追踪',
  `IS_COMPLETEABLE_` bit(1) DEFAULT NULL COMMENT 'IS_COMPLETEABLE_ 引擎属性，供 Flowable 案例管理RU案例实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REFERENCE 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用目标类型，供引擎解析关联对象',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `LAST_REACTIVATION_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_REACTIVATION 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_REACTIVATION_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LAST_REACTIVATION_USER 标识，关联 Flowable 引擎中的对应记录',
  `BUSINESS_STATUS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务状态，供引擎和应用同步实例进度',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_CASE_INST_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_CASE_INST_PARENT` (`PARENT_ID_`),
  KEY `ACT_IDX_CASE_INST_REF_ID_` (`REFERENCE_ID_`),
  CONSTRAINT `ACT_FK_CASE_INST_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `ACT_CMMN_CASEDEF` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理RU案例实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_RU_MIL_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `TIME_STAMP_` datetime(3) DEFAULT NULL COMMENT '事件时间戳，用于排序和审计追踪',
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CASE_INST 标识，关联 Flowable 引擎中的对应记录',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_MIL_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_MIL_CASE_INST` (`CASE_INST_ID_`),
  CONSTRAINT `ACT_FK_MIL_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `ACT_CMMN_CASEDEF` (`ID_`),
  CONSTRAINT `ACT_FK_MIL_CASE_INST` FOREIGN KEY (`CASE_INST_ID_`) REFERENCES `ACT_CMMN_RU_CASE_INST` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理RU里程碑实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_RU_PLAN_ITEM_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_INST 标识，关联 Flowable 引擎中的对应记录',
  `STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'STAGE_INST 标识，关联 Flowable 引擎中的对应记录',
  `IS_STAGE_` bit(1) DEFAULT NULL COMMENT 'IS_STAGE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `STATE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎状态，决定实例或作业后续可执行操作',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_USER 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REFERENCE 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用目标类型，供引擎解析关联对象',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `ITEM_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ITEM_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `ITEM_DEFINITION_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ITEM_DEFINITION_TYPE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IS_COMPLETEABLE_` bit(1) DEFAULT NULL COMMENT 'IS_COMPLETEABLE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IS_COUNT_ENABLED_` bit(1) DEFAULT NULL COMMENT '计数开关，控制是否维护关联对象数量',
  `VAR_COUNT_` int DEFAULT NULL COMMENT 'VAR 数量，供 Flowable 引擎统计运行状态',
  `SENTRY_PART_INST_COUNT_` int DEFAULT NULL COMMENT 'SENTRY_PART_INST 数量，供 Flowable 引擎统计运行状态',
  `LAST_AVAILABLE_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_AVAILABLE 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_ENABLED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_ENABLED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_DISABLED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_DISABLED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_STARTED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_STARTED 时间，供 Flowable 引擎排序和历史追踪',
  `LAST_SUSPENDED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_SUSPENDED 时间，供 Flowable 引擎排序和历史追踪',
  `COMPLETED_TIME_` datetime(3) DEFAULT NULL COMMENT 'COMPLETED 时间，供 Flowable 引擎排序和历史追踪',
  `OCCURRED_TIME_` datetime(3) DEFAULT NULL COMMENT 'OCCURRED 时间，供 Flowable 引擎排序和历史追踪',
  `TERMINATED_TIME_` datetime(3) DEFAULT NULL COMMENT 'TERMINATED 时间，供 Flowable 引擎排序和历史追踪',
  `EXIT_TIME_` datetime(3) DEFAULT NULL COMMENT 'EXIT 时间，供 Flowable 引擎排序和历史追踪',
  `ENDED_TIME_` datetime(3) DEFAULT NULL COMMENT 'ENDED 时间，供 Flowable 引擎排序和历史追踪',
  `ENTRY_CRITERION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ENTRY_CRITERION 标识，关联 Flowable 引擎中的对应记录',
  `EXIT_CRITERION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXIT_CRITERION 标识，关联 Flowable 引擎中的对应记录',
  `EXTRA_VALUE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXTRA_VALUE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DERIVED_CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DERIVED_CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `LAST_UNAVAILABLE_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_UNAVAILABLE 时间，供 Flowable 引擎排序和历史追踪',
  `ASSIGNEE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办理人标识，供任务分派和待办查询',
  `COMPLETED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完成人标识，供历史记录追溯责任人',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_PLAN_ITEM_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_PLAN_ITEM_CASE_INST` (`CASE_INST_ID_`),
  KEY `ACT_IDX_PLAN_ITEM_STAGE_INST` (`STAGE_INST_ID_`),
  CONSTRAINT `ACT_FK_PLAN_ITEM_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `ACT_CMMN_CASEDEF` (`ID_`),
  CONSTRAINT `ACT_FK_PLAN_ITEM_CASE_INST` FOREIGN KEY (`CASE_INST_ID_`) REFERENCES `ACT_CMMN_RU_CASE_INST` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_CMMN_RU_SENTRY_PART_INST` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int NOT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CASE_DEF_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录',
  `CASE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CASE_INST 标识，关联 Flowable 引擎中的对应记录',
  `PLAN_ITEM_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PLAN_ITEM_INST 标识，关联 Flowable 引擎中的对应记录',
  `ON_PART_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ON_PART 标识，关联 Flowable 引擎中的对应记录',
  `IF_PART_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IF_PART 标识，关联 Flowable 引擎中的对应记录',
  `TIME_STAMP_` datetime(3) DEFAULT NULL COMMENT '事件时间戳，用于排序和审计追踪',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_SENTRY_CASE_DEF` (`CASE_DEF_ID_`),
  KEY `ACT_IDX_SENTRY_CASE_INST` (`CASE_INST_ID_`),
  KEY `ACT_IDX_SENTRY_PLAN_ITEM` (`PLAN_ITEM_INST_ID_`),
  CONSTRAINT `ACT_FK_SENTRY_CASE_DEF` FOREIGN KEY (`CASE_DEF_ID_`) REFERENCES `ACT_CMMN_CASEDEF` (`ID_`),
  CONSTRAINT `ACT_FK_SENTRY_CASE_INST` FOREIGN KEY (`CASE_INST_ID_`) REFERENCES `ACT_CMMN_RU_CASE_INST` (`ID_`),
  CONSTRAINT `ACT_FK_SENTRY_PLAN_ITEM` FOREIGN KEY (`PLAN_ITEM_INST_ID_`) REFERENCES `ACT_CMMN_RU_PLAN_ITEM_INST` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 案例管理RU入口条件条件部分实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_DMN_DECISION` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `VERSION_` int DEFAULT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `RESOURCE_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源文件名，定位部署包内的模型内容',
  `DESCRIPTION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `DECISION_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DECISION_TYPE_ 引擎属性，供 Flowable 决策规则决策定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_DMN_DEC_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 决策规则决策定义表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_DMN_DEPLOYMENT` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL COMMENT '部署时间，用于模型版本管理和历史追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 决策规则部署包表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_DMN_DEPLOYMENT_RESOURCE` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_BYTES_` longblob COMMENT '资源二进制内容，保存部署时上传的模型文件',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_DMN_RSRC_DPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_DMN_RSRC_DPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_DMN_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 决策规则部署包资源文件表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_DMN_HI_DECISION_EXECUTION` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `DECISION_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DECISION_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `START_TIME_` datetime(3) DEFAULT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `END_TIME_` datetime(3) DEFAULT NULL COMMENT '结束时间，用于判定实例完成和历史统计',
  `INSTANCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `EXECUTION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `ACTIVITY_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACTIVITY 标识，关联 Flowable 引擎中的对应记录',
  `FAILED_` bit(1) DEFAULT b'0' COMMENT 'FAILED_ 引擎属性，供 Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `EXECUTION_JSON_` longtext COLLATE utf8mb4_unicode_ci COMMENT 'EXECUTION_JSON_ 引擎属性，供 Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_DMN_INSTANCE_ID` (`INSTANCE_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_EVT_LOG` (
  `LOG_NR_` bigint NOT NULL AUTO_INCREMENT COMMENT 'LOG_NR_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `TIME_STAMP_` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件时间戳，用于排序和审计追踪',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `DATA_` longblob COMMENT 'DATA_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK 时间，供 Flowable 引擎排序和历史追踪',
  `IS_PROCESSED_` tinyint DEFAULT '0' COMMENT 'IS_PROCESSED_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`LOG_NR_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件事件日志表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_GE_BYTEARRAY` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `DEPLOYMENT_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `BYTES_` longblob COMMENT 'BYTES_ 引擎属性，供 Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `GENERATED_` tinyint DEFAULT NULL COMMENT 'GENERATED_ 引擎属性，供 Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_BYTEAR_DEPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_BYTEARR_DEPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_RE_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_GE_PROPERTY` (
  `NAME_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `VALUE_` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '属性值，供引擎读取配置和版本信息',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  PRIMARY KEY (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 通用资源引擎属性表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_ACTINST` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT '1' COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `ACT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ACT 标识，关联 Flowable 引擎中的对应记录',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `CALL_PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CALL_PROC_INST 标识，关联 Flowable 引擎中的对应记录',
  `ACT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACT_NAME_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ACT_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ACT_TYPE_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ASSIGNEE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办理人标识，供任务分派和待办查询',
  `START_TIME_` datetime(3) NOT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `END_TIME_` datetime(3) DEFAULT NULL COMMENT '结束时间，用于判定实例完成和历史统计',
  `TRANSACTION_ORDER_` int DEFAULT NULL COMMENT 'TRANSACTION_ORDER_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DURATION_` bigint DEFAULT NULL COMMENT '持续时长，供历史查询和统计分析',
  `DELETE_REASON_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '删除或取消原因，保留在历史记录中供审计',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `COMPLETED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完成人标识，供历史记录追溯责任人',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_ACT_INST_START` (`START_TIME_`),
  KEY `ACT_IDX_HI_ACT_INST_END` (`END_TIME_`),
  KEY `ACT_IDX_HI_ACT_INST_PROCINST` (`PROC_INST_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_HI_ACT_INST_EXEC` (`EXECUTION_ID_`,`ACT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录活动实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_ATTACHMENT` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `URL_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'URL_ 引擎属性，供 Flowable 历史记录附件表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CONTENT_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CONTENT 标识，关联 Flowable 引擎中的对应记录',
  `TIME_` datetime(3) DEFAULT NULL COMMENT '发生时间，供引擎事件排序和历史追踪',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录附件表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_COMMENT` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `TIME_` datetime(3) NOT NULL COMMENT '发生时间，供引擎事件排序和历史追踪',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `ACTION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACTION_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `MESSAGE_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MESSAGE_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `FULL_MSG_` longblob COMMENT 'FULL_MSG_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录批注表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_DETAIL` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `ACT_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACT_INST 标识，关联 Flowable 引擎中的对应记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `VAR_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'VAR_TYPE_ 引擎属性，供 Flowable 历史记录变量详情表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `TIME_` datetime(3) NOT NULL COMMENT '发生时间，供引擎事件排序和历史追踪',
  `BYTEARRAY_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录',
  `DOUBLE_` double DEFAULT NULL COMMENT '浮点变量值，供流程表达式和任务处理读取',
  `LONG_` bigint DEFAULT NULL COMMENT '整数变量值，供流程表达式和任务处理读取',
  `TEXT_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文本变量值，供流程表达式和任务处理读取',
  `TEXT2_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第二段文本值，保存补充变量或序列化内容',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_DETAIL_PROC_INST` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_DETAIL_ACT_INST` (`ACT_INST_ID_`),
  KEY `ACT_IDX_HI_DETAIL_TIME` (`TIME_`),
  KEY `ACT_IDX_HI_DETAIL_NAME` (`NAME_`),
  KEY `ACT_IDX_HI_DETAIL_TASK_ID` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录变量详情表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_ENTITYLINK` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `LINK_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LINK_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `PARENT_ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `REF_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REF_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `REF_SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REF_SCOPE_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `REF_SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REF_SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `ROOT_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ROOT_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `ROOT_SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ROOT_SCOPE_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `HIERARCHY_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HIERARCHY_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_ENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_REF_SCOPE` (`REF_SCOPE_ID_`,`REF_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_ROOT_SCOPE` (`ROOT_SCOPE_ID_`,`ROOT_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_HI_ENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录实体关联表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_IDENTITYLINK` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `GROUP_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'GROUP 标识，关联 Flowable 引擎中的对应记录',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_USER` (`USER_ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_IDENT_LNK_TASK` (`TASK_ID_`),
  KEY `ACT_IDX_HI_IDENT_LNK_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录参与者关联表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_PROCINST` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT '1' COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `BUSINESS_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务键，供应用按业务记录查找流程实例',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `START_TIME_` datetime(3) NOT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `END_TIME_` datetime(3) DEFAULT NULL COMMENT '结束时间，用于判定实例完成和历史统计',
  `DURATION_` bigint DEFAULT NULL COMMENT '持续时长，供历史查询和统计分析',
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_USER 标识，关联 Flowable 引擎中的对应记录',
  `START_ACT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_ACT 标识，关联 Flowable 引擎中的对应记录',
  `END_ACT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'END_ACT 标识，关联 Flowable 引擎中的对应记录',
  `SUPER_PROCESS_INSTANCE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUPER_PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `DELETE_REASON_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '删除或取消原因，保留在历史记录中供审计',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `CALLBACK_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CALLBACK 标识，关联 Flowable 引擎中的对应记录',
  `CALLBACK_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回调类型，决定异步结果处理方式',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REFERENCE 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用目标类型，供引擎解析关联对象',
  `PROPAGATED_STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录',
  `BUSINESS_STATUS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务状态，供引擎和应用同步实例进度',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `PROC_INST_ID_` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_PRO_INST_END` (`END_TIME_`),
  KEY `ACT_IDX_HI_PRO_I_BUSKEY` (`BUSINESS_KEY_`),
  KEY `ACT_IDX_HI_PRO_SUPER_PROCINST` (`SUPER_PROCESS_INSTANCE_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录流程实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_TASKINST` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT '1' COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `TASK_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TASK_DEF 标识，关联 Flowable 引擎中的对应记录',
  `TASK_DEF_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TASK_DEF_KEY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `PROPAGATED_STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `PARENT_TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_TASK 标识，关联 Flowable 引擎中的对应记录',
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'OWNER_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ASSIGNEE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办理人标识，供任务分派和待办查询',
  `START_TIME_` datetime(3) NOT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `CLAIM_TIME_` datetime(3) DEFAULT NULL COMMENT 'CLAIM 时间，供 Flowable 引擎排序和历史追踪',
  `END_TIME_` datetime(3) DEFAULT NULL COMMENT '结束时间，用于判定实例完成和历史统计',
  `DURATION_` bigint DEFAULT NULL COMMENT '持续时长，供历史查询和统计分析',
  `DELETE_REASON_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '删除或取消原因，保留在历史记录中供审计',
  `PRIORITY_` int DEFAULT NULL COMMENT 'PRIORITY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DUE_DATE_` datetime(3) DEFAULT NULL COMMENT 'DUE_DATE_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `FORM_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FORM_KEY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪',
  `STATE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎状态，决定实例或作业后续可执行操作',
  `IN_PROGRESS_TIME_` datetime(3) DEFAULT NULL COMMENT 'IN_PROGRESS 时间，供 Flowable 引擎排序和历史追踪',
  `IN_PROGRESS_STARTED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IN_PROGRESS_STARTED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CLAIMED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CLAIMED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SUSPENDED_TIME_` datetime(3) DEFAULT NULL COMMENT 'SUSPENDED 时间，供 Flowable 引擎排序和历史追踪',
  `SUSPENDED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUSPENDED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `COMPLETED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完成人标识，供历史记录追溯责任人',
  `IN_PROGRESS_DUE_DATE_` datetime(3) DEFAULT NULL COMMENT 'IN_PROGRESS_DUE_DATE_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_TASK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_TASK_INST_PROCINST` (`PROC_INST_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录任务实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_TSK_LOG` (
  `ID_` bigint NOT NULL AUTO_INCREMENT COMMENT '引擎记录主键，供内部表关联和状态更新',
  `TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `TIME_STAMP_` timestamp(3) NOT NULL COMMENT '事件时间戳，用于排序和审计追踪',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `DATA_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DATA_ 引擎属性，供 Flowable 历史记录任务事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_ACT_HI_TSK_LOG_TASK` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录任务事件日志表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_HI_VARINST` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT '1' COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `VAR_TYPE_` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'VAR_TYPE_ 引擎属性，供 Flowable 历史记录变量实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `BYTEARRAY_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录',
  `DOUBLE_` double DEFAULT NULL COMMENT '浮点变量值，供流程表达式和任务处理读取',
  `LONG_` bigint DEFAULT NULL COMMENT '整数变量值，供流程表达式和任务处理读取',
  `TEXT_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文本变量值，供流程表达式和任务处理读取',
  `TEXT2_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第二段文本值，保存补充变量或序列化内容',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `LAST_UPDATED_TIME_` datetime(3) DEFAULT NULL COMMENT 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪',
  `META_INFO_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元数据，保存编辑器和发布需要的补充信息',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_HI_PROCVAR_NAME_TYPE` (`NAME_`,`VAR_TYPE_`),
  KEY `ACT_IDX_HI_VAR_SCOPE_ID_TYPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_VAR_SUB_ID_TYPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_HI_PROCVAR_PROC_INST` (`PROC_INST_ID_`),
  KEY `ACT_IDX_HI_PROCVAR_TASK_ID` (`TASK_ID_`),
  KEY `ACT_IDX_HI_PROCVAR_EXE` (`EXECUTION_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 历史记录变量实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_BYTEARRAY` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `BYTES_` longblob COMMENT 'BYTES_ 引擎属性，供 Flowable 身份管理二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理二进制资源表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_GROUP` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理用户组表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_INFO` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `USER_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `VALUE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '属性值，供引擎读取配置和版本信息',
  `PASSWORD_` longblob COMMENT 'PASSWORD_ 引擎属性，供 Flowable 身份管理用户资料表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PARENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理用户资料表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_MEMBERSHIP` (
  `USER_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `GROUP_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'GROUP 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`USER_ID_`,`GROUP_ID_`),
  KEY `ACT_FK_MEMB_GROUP` (`GROUP_ID_`),
  CONSTRAINT `ACT_FK_MEMB_GROUP` FOREIGN KEY (`GROUP_ID_`) REFERENCES `ACT_ID_GROUP` (`ID_`),
  CONSTRAINT `ACT_FK_MEMB_USER` FOREIGN KEY (`USER_ID_`) REFERENCES `ACT_ID_USER` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理用户组成员表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_PRIV` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_PRIV_NAME` (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理权限表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_PRIV_MAPPING` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `PRIV_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PRIV 标识，关联 Flowable 引擎中的对应记录',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `GROUP_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'GROUP 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_PRIV_MAPPING` (`PRIV_ID_`),
  KEY `ACT_IDX_PRIV_USER` (`USER_ID_`),
  KEY `ACT_IDX_PRIV_GROUP` (`GROUP_ID_`),
  CONSTRAINT `ACT_FK_PRIV_MAPPING` FOREIGN KEY (`PRIV_ID_`) REFERENCES `ACT_ID_PRIV` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理权限权限映射表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_PROPERTY` (
  `NAME_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `VALUE_` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '属性值，供引擎读取配置和版本信息',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  PRIMARY KEY (`NAME_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理引擎属性表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_TOKEN` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `TOKEN_VALUE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TOKEN_VALUE_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `TOKEN_DATE_` timestamp(3) NULL DEFAULT NULL COMMENT 'TOKEN_DATE_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IP_ADDRESS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IP_ADDRESS_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `USER_AGENT_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER_AGENT_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `TOKEN_DATA_` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TOKEN_DATA_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_ID_USER` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `FIRST_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FIRST_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `LAST_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LAST_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DISPLAY_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DISPLAY_NAME_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `EMAIL_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EMAIL_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PWD_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PWD_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PICTURE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PICTURE 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 身份管理用户表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_PROCDEF_INFO` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `INFO_JSON_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'INFO_JSON 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_INFO_PROCDEF` (`PROC_DEF_ID_`),
  KEY `ACT_IDX_INFO_PROCDEF` (`PROC_DEF_ID_`),
  KEY `ACT_FK_INFO_JSON_BA` (`INFO_JSON_ID_`),
  CONSTRAINT `ACT_FK_INFO_JSON_BA` FOREIGN KEY (`INFO_JSON_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_INFO_PROCDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 流程引擎用户资料表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RE_DEPLOYMENT` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `DEPLOY_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '部署时间，用于模型版本管理和历史追踪',
  `DERIVED_FROM_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DERIVED_FROM_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DERIVED_FROM_ROOT_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DERIVED_FROM_ROOT_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录',
  `ENGINE_VERSION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ENGINE_VERSION_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 模型仓库部署包表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RE_MODEL` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `LAST_UPDATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LAST_UPDATE 时间，供 Flowable 引擎排序和历史追踪',
  `VERSION_` int DEFAULT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `META_INFO_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元数据，保存编辑器和发布需要的补充信息',
  `DEPLOYMENT_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `EDITOR_SOURCE_VALUE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EDITOR_SOURCE_VALUE 标识，关联 Flowable 引擎中的对应记录',
  `EDITOR_SOURCE_EXTRA_VALUE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EDITOR_SOURCE_EXTRA_VALUE 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_FK_MODEL_SOURCE` (`EDITOR_SOURCE_VALUE_ID_`),
  KEY `ACT_FK_MODEL_SOURCE_EXTRA` (`EDITOR_SOURCE_EXTRA_VALUE_ID_`),
  KEY `ACT_FK_MODEL_DEPLOYMENT` (`DEPLOYMENT_ID_`),
  CONSTRAINT `ACT_FK_MODEL_DEPLOYMENT` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `ACT_RE_DEPLOYMENT` (`ID_`),
  CONSTRAINT `ACT_FK_MODEL_SOURCE` FOREIGN KEY (`EDITOR_SOURCE_VALUE_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_MODEL_SOURCE_EXTRA` FOREIGN KEY (`EDITOR_SOURCE_EXTRA_VALUE_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 模型仓库模型表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RE_PROCDEF` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `VERSION_` int NOT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `DEPLOYMENT_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源文件名，定位部署包内的模型内容',
  `DGRM_RESOURCE_NAME_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DGRM_RESOURCE_NAME_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `HAS_START_FORM_KEY_` tinyint DEFAULT NULL COMMENT 'HAS_START_FORM_KEY_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `HAS_GRAPHICAL_NOTATION_` tinyint DEFAULT NULL COMMENT 'HAS_GRAPHICAL_NOTATION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SUSPENSION_STATE_` int DEFAULT NULL COMMENT '挂起状态，控制定义或实例能否继续执行',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `ENGINE_VERSION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ENGINE_VERSION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DERIVED_FROM_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DERIVED_FROM_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DERIVED_FROM_ROOT_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DERIVED_FROM_ROOT_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DERIVED_VERSION_` int NOT NULL DEFAULT '0' COMMENT 'DERIVED_VERSION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_UNIQ_PROCDEF` (`KEY_`,`VERSION_`,`DERIVED_VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_ACTINST` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT '1' COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `ACT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ACT 标识，关联 Flowable 引擎中的对应记录',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `CALL_PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CALL_PROC_INST 标识，关联 Flowable 引擎中的对应记录',
  `ACT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACT_NAME_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ACT_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ACT_TYPE_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ASSIGNEE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办理人标识，供任务分派和待办查询',
  `START_TIME_` datetime(3) NOT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `END_TIME_` datetime(3) DEFAULT NULL COMMENT '结束时间，用于判定实例完成和历史统计',
  `DURATION_` bigint DEFAULT NULL COMMENT '持续时长，供历史查询和统计分析',
  `TRANSACTION_ORDER_` int DEFAULT NULL COMMENT 'TRANSACTION_ORDER_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `DELETE_REASON_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '删除或取消原因，保留在历史记录中供审计',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `COMPLETED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完成人标识，供历史记录追溯责任人',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_RU_ACTI_START` (`START_TIME_`),
  KEY `ACT_IDX_RU_ACTI_END` (`END_TIME_`),
  KEY `ACT_IDX_RU_ACTI_PROC` (`PROC_INST_ID_`),
  KEY `ACT_IDX_RU_ACTI_PROC_ACT` (`PROC_INST_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_RU_ACTI_EXEC` (`EXECUTION_ID_`),
  KEY `ACT_IDX_RU_ACTI_EXEC_ACT` (`EXECUTION_ID_`,`ACT_ID_`),
  KEY `ACT_IDX_RU_ACTI_TASK` (`TASK_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时活动实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_DEADLETTER_JOB` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL COMMENT '独占执行标记，控制同一流程实例的作业并发',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROCESS_INSTANCE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元素名称，供引擎历史记录展示',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `CORRELATION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CORRELATION 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_STACK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_MSG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近异常信息，供失败作业排查与重试',
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL COMMENT '到期时间，用于定时作业调度',
  `REPEAT_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重复周期，控制定时作业的后续触发',
  `HANDLER_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器类型，决定作业调用的执行逻辑',
  `HANDLER_CFG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器配置，向作业执行逻辑传递参数',
  `CUSTOM_VALUES_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_DEADLETTER_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_DJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_DJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_DJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_DEADLETTER_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_DEADLETTER_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时死信作业表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_ENTITYLINK` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `LINK_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LINK_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `PARENT_ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `REF_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REF_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `REF_SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REF_SCOPE_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `REF_SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REF_SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `ROOT_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ROOT_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `ROOT_SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ROOT_SCOPE_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `HIERARCHY_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HIERARCHY_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_ENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_REF_SCOPE` (`REF_SCOPE_ID_`,`REF_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_ROOT_SCOPE` (`ROOT_SCOPE_ID_`,`ROOT_SCOPE_TYPE_`,`LINK_TYPE_`),
  KEY `ACT_IDX_ENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`,`LINK_TYPE_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时实体关联表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_EVENT_SUBSCR` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `EVENT_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'EVENT_TYPE_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `EVENT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EVENT_NAME_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `ACTIVITY_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACTIVITY 标识，关联 Flowable 引擎中的对应记录',
  `CONFIGURATION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CONFIGURATION_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CREATED_` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'CREATED_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `SUB_SCOPE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_DEFINITION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK 时间，供 Flowable 引擎排序和历史追踪',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `SCOPE_DEFINITION_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION_KEY_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EVENT_SUBSCR_CONFIG_` (`CONFIGURATION_`),
  KEY `ACT_IDX_EVENT_SUBSCR_SCOPEREF_` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EVENT_SUBSCR_EXEC_ID` (`EXECUTION_ID_`),
  KEY `ACT_IDX_EVENT_SUBSCR_PROC_ID` (`PROC_INST_ID_`),
  CONSTRAINT `ACT_FK_EVENT_EXEC` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时事件订阅表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_EXECUTION` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `BUSINESS_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务键，供应用按业务记录查找流程实例',
  `PARENT_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT 标识，关联 Flowable 引擎中的对应记录',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `SUPER_EXEC_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUPER_EXEC_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ROOT_PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ROOT_PROC_INST 标识，关联 Flowable 引擎中的对应记录',
  `ACT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACT 标识，关联 Flowable 引擎中的对应记录',
  `IS_ACTIVE_` tinyint DEFAULT NULL COMMENT 'IS_ACTIVE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IS_CONCURRENT_` tinyint DEFAULT NULL COMMENT 'IS_CONCURRENT_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IS_SCOPE_` tinyint DEFAULT NULL COMMENT 'IS_SCOPE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IS_EVENT_SCOPE_` tinyint DEFAULT NULL COMMENT 'IS_EVENT_SCOPE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IS_MI_ROOT_` tinyint DEFAULT NULL COMMENT 'IS_MI_ROOT_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SUSPENSION_STATE_` int DEFAULT NULL COMMENT '挂起状态，控制定义或实例能否继续执行',
  `CACHED_ENT_STATE_` int DEFAULT NULL COMMENT 'CACHED_ENT_STATE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `START_ACT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_ACT 标识，关联 Flowable 引擎中的对应记录',
  `START_TIME_` datetime(3) DEFAULT NULL COMMENT '开始时间，用于计算执行和处理时长',
  `START_USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'START_USER 标识，关联 Flowable 引擎中的对应记录',
  `LOCK_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK 时间，供 Flowable 引擎排序和历史追踪',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `IS_COUNT_ENABLED_` tinyint DEFAULT NULL COMMENT '计数开关，控制是否维护关联对象数量',
  `EVT_SUBSCR_COUNT_` int DEFAULT NULL COMMENT 'EVT_SUBSCR 数量，供 Flowable 引擎统计运行状态',
  `TASK_COUNT_` int DEFAULT NULL COMMENT 'TASK 数量，供 Flowable 引擎统计运行状态',
  `JOB_COUNT_` int DEFAULT NULL COMMENT 'JOB 数量，供 Flowable 引擎统计运行状态',
  `TIMER_JOB_COUNT_` int DEFAULT NULL COMMENT 'TIMER_JOB 数量，供 Flowable 引擎统计运行状态',
  `SUSP_JOB_COUNT_` int DEFAULT NULL COMMENT 'SUSP_JOB 数量，供 Flowable 引擎统计运行状态',
  `DEADLETTER_JOB_COUNT_` int DEFAULT NULL COMMENT 'DEADLETTER_JOB 数量，供 Flowable 引擎统计运行状态',
  `EXTERNAL_WORKER_JOB_COUNT_` int DEFAULT NULL COMMENT 'EXTERNAL_WORKER_JOB 数量，供 Flowable 引擎统计运行状态',
  `VAR_COUNT_` int DEFAULT NULL COMMENT 'VAR 数量，供 Flowable 引擎统计运行状态',
  `ID_LINK_COUNT_` int DEFAULT NULL COMMENT 'ID_LINK 数量，供 Flowable 引擎统计运行状态',
  `CALLBACK_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CALLBACK 标识，关联 Flowable 引擎中的对应记录',
  `CALLBACK_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回调类型，决定异步结果处理方式',
  `REFERENCE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'REFERENCE 标识，关联 Flowable 引擎中的对应记录',
  `REFERENCE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用目标类型，供引擎解析关联对象',
  `PROPAGATED_STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录',
  `BUSINESS_STATUS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务状态，供引擎和应用同步实例进度',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EXEC_BUSKEY` (`BUSINESS_KEY_`),
  KEY `ACT_IDC_EXEC_ROOT` (`ROOT_PROC_INST_ID_`),
  KEY `ACT_IDX_EXEC_REF_ID_` (`REFERENCE_ID_`),
  KEY `ACT_FK_EXE_PROCINST` (`PROC_INST_ID_`),
  KEY `ACT_FK_EXE_PARENT` (`PARENT_ID_`),
  KEY `ACT_FK_EXE_SUPER` (`SUPER_EXEC_`),
  KEY `ACT_FK_EXE_PROCDEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_EXE_PARENT` FOREIGN KEY (`PARENT_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`) ON DELETE CASCADE,
  CONSTRAINT `ACT_FK_EXE_PROCDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_EXE_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `ACT_FK_EXE_SUPER` FOREIGN KEY (`SUPER_EXEC_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时执行实例表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_EXTERNAL_JOB` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL COMMENT '独占执行标记，控制同一流程实例的作业并发',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROCESS_INSTANCE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元素名称，供引擎历史记录展示',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `CORRELATION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CORRELATION 标识，关联 Flowable 引擎中的对应记录',
  `RETRIES_` int DEFAULT NULL COMMENT '剩余重试次数，失败后用于决定是否继续调度',
  `EXCEPTION_STACK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_MSG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近异常信息，供失败作业排查与重试',
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL COMMENT '到期时间，用于定时作业调度',
  `REPEAT_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重复周期，控制定时作业的后续触发',
  `HANDLER_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器类型，决定作业调用的执行逻辑',
  `HANDLER_CFG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器配置，向作业执行逻辑传递参数',
  `CUSTOM_VALUES_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_EXTERNAL_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_EJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_EJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  CONSTRAINT `ACT_FK_EXTERNAL_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_EXTERNAL_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时外部作业表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_HISTORY_JOB` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `RETRIES_` int DEFAULT NULL COMMENT '剩余重试次数，失败后用于决定是否继续调度',
  `EXCEPTION_STACK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_MSG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近异常信息，供失败作业排查与重试',
  `HANDLER_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器类型，决定作业调用的执行逻辑',
  `HANDLER_CFG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器配置，向作业执行逻辑传递参数',
  `CUSTOM_VALUES_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录',
  `ADV_HANDLER_CFG_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ADV_HANDLER_CFG 标识，关联 Flowable 引擎中的对应记录',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时历史作业表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_IDENTITYLINK` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `GROUP_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'GROUP 标识，关联 Flowable 引擎中的对应记录',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `USER_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'USER 标识，关联 Flowable 引擎中的对应记录',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_IDENT_LNK_USER` (`USER_ID_`),
  KEY `ACT_IDX_IDENT_LNK_GROUP` (`GROUP_ID_`),
  KEY `ACT_IDX_IDENT_LNK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_IDENT_LNK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_IDENT_LNK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_ATHRZ_PROCEDEF` (`PROC_DEF_ID_`),
  KEY `ACT_FK_TSKASS_TASK` (`TASK_ID_`),
  KEY `ACT_FK_IDL_PROCINST` (`PROC_INST_ID_`),
  CONSTRAINT `ACT_FK_ATHRZ_PROCEDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_IDL_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_TSKASS_TASK` FOREIGN KEY (`TASK_ID_`) REFERENCES `ACT_RU_TASK` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时参与者关联表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_JOB` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL COMMENT '独占执行标记，控制同一流程实例的作业并发',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROCESS_INSTANCE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元素名称，供引擎历史记录展示',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `CORRELATION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CORRELATION 标识，关联 Flowable 引擎中的对应记录',
  `RETRIES_` int DEFAULT NULL COMMENT '剩余重试次数，失败后用于决定是否继续调度',
  `EXCEPTION_STACK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_MSG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近异常信息，供失败作业排查与重试',
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL COMMENT '到期时间，用于定时作业调度',
  `REPEAT_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重复周期，控制定时作业的后续触发',
  `HANDLER_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器类型，决定作业调用的执行逻辑',
  `HANDLER_CFG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器配置，向作业执行逻辑传递参数',
  `CUSTOM_VALUES_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_JOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_JOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_JOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时作业表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_SUSPENDED_JOB` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL COMMENT '独占执行标记，控制同一流程实例的作业并发',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROCESS_INSTANCE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元素名称，供引擎历史记录展示',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `CORRELATION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CORRELATION 标识，关联 Flowable 引擎中的对应记录',
  `RETRIES_` int DEFAULT NULL COMMENT '剩余重试次数，失败后用于决定是否继续调度',
  `EXCEPTION_STACK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_MSG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近异常信息，供失败作业排查与重试',
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL COMMENT '到期时间，用于定时作业调度',
  `REPEAT_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重复周期，控制定时作业的后续触发',
  `HANDLER_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器类型，决定作业调用的执行逻辑',
  `HANDLER_CFG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器配置，向作业执行逻辑传递参数',
  `CUSTOM_VALUES_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_SUSPENDED_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_SJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_SJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_SJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_SUSPENDED_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_SUSPENDED_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时挂起作业表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_TASK` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `TASK_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TASK_DEF 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `PROPAGATED_STAGE_INST_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `PARENT_TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_TASK 标识，关联 Flowable 引擎中的对应记录',
  `DESCRIPTION_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `TASK_DEF_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TASK_DEF_KEY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'OWNER_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `ASSIGNEE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办理人标识，供任务分派和待办查询',
  `DELEGATION_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DELEGATION_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `PRIORITY_` int DEFAULT NULL COMMENT 'PRIORITY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `DUE_DATE_` datetime(3) DEFAULT NULL COMMENT 'DUE_DATE_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `SUSPENSION_STATE_` int DEFAULT NULL COMMENT '挂起状态，控制定义或实例能否继续执行',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `FORM_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FORM_KEY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CLAIM_TIME_` datetime(3) DEFAULT NULL COMMENT 'CLAIM 时间，供 Flowable 引擎排序和历史追踪',
  `IS_COUNT_ENABLED_` tinyint DEFAULT NULL COMMENT '计数开关，控制是否维护关联对象数量',
  `VAR_COUNT_` int DEFAULT NULL COMMENT 'VAR 数量，供 Flowable 引擎统计运行状态',
  `ID_LINK_COUNT_` int DEFAULT NULL COMMENT 'ID_LINK 数量，供 Flowable 引擎统计运行状态',
  `SUB_TASK_COUNT_` int DEFAULT NULL COMMENT 'SUB_TASK 数量，供 Flowable 引擎统计运行状态',
  `STATE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎状态，决定实例或作业后续可执行操作',
  `IN_PROGRESS_TIME_` datetime(3) DEFAULT NULL COMMENT 'IN_PROGRESS 时间，供 Flowable 引擎排序和历史追踪',
  `IN_PROGRESS_STARTED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IN_PROGRESS_STARTED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `CLAIMED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CLAIMED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `SUSPENDED_TIME_` datetime(3) DEFAULT NULL COMMENT 'SUSPENDED 时间，供 Flowable 引擎排序和历史追踪',
  `SUSPENDED_BY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUSPENDED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  `IN_PROGRESS_DUE_DATE_` datetime(3) DEFAULT NULL COMMENT 'IN_PROGRESS_DUE_DATE_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_TASK_CREATE` (`CREATE_TIME_`),
  KEY `ACT_IDX_TASK_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TASK_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TASK_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_TASK_EXE` (`EXECUTION_ID_`),
  KEY `ACT_FK_TASK_PROCINST` (`PROC_INST_ID_`),
  KEY `ACT_FK_TASK_PROCDEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_TASK_EXE` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_TASK_PROCDEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_TASK_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时任务表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_TIMER_JOB` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `LOCK_EXP_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪',
  `LOCK_OWNER_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '锁持有者，防止同一作业被多个执行器重复处理',
  `EXCLUSIVE_` tinyint(1) DEFAULT NULL COMMENT '独占执行标记，控制同一流程实例的作业并发',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROCESS_INSTANCE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录',
  `PROC_DEF_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID，关联已部署的流程模型',
  `ELEMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ELEMENT 标识，关联 Flowable 引擎中的对应记录',
  `ELEMENT_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元素名称，供引擎历史记录展示',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SCOPE_DEFINITION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录',
  `CORRELATION_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CORRELATION 标识，关联 Flowable 引擎中的对应记录',
  `RETRIES_` int DEFAULT NULL COMMENT '剩余重试次数，失败后用于决定是否继续调度',
  `EXCEPTION_STACK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录',
  `EXCEPTION_MSG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近异常信息，供失败作业排查与重试',
  `DUEDATE_` timestamp(3) NULL DEFAULT NULL COMMENT '到期时间，用于定时作业调度',
  `REPEAT_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重复周期，控制定时作业的后续触发',
  `HANDLER_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器类型，决定作业调用的执行逻辑',
  `HANDLER_CFG_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器配置，向作业执行逻辑传递参数',
  `CUSTOM_VALUES_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录',
  `CREATE_TIME_` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_TIMER_JOB_EXCEPTION_STACK_ID` (`EXCEPTION_STACK_ID_`),
  KEY `ACT_IDX_TIMER_JOB_CUSTOM_VALUES_ID` (`CUSTOM_VALUES_ID_`),
  KEY `ACT_IDX_TIMER_JOB_CORRELATION_ID` (`CORRELATION_ID_`),
  KEY `ACT_IDX_TIMER_JOB_DUEDATE` (`DUEDATE_`),
  KEY `ACT_IDX_TJOB_SCOPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TJOB_SUB_SCOPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_TJOB_SCOPE_DEF` (`SCOPE_DEFINITION_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_TIMER_JOB_EXECUTION` (`EXECUTION_ID_`),
  KEY `ACT_FK_TIMER_JOB_PROCESS_INSTANCE` (`PROCESS_INSTANCE_ID_`),
  KEY `ACT_FK_TIMER_JOB_PROC_DEF` (`PROC_DEF_ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_CUSTOM_VALUES` FOREIGN KEY (`CUSTOM_VALUES_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_EXCEPTION` FOREIGN KEY (`EXCEPTION_STACK_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_EXECUTION` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_PROC_DEF` FOREIGN KEY (`PROC_DEF_ID_`) REFERENCES `ACT_RE_PROCDEF` (`ID_`),
  CONSTRAINT `ACT_FK_TIMER_JOB_PROCESS_INSTANCE` FOREIGN KEY (`PROCESS_INSTANCE_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时定时作业表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACT_RU_VARIABLE` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，供引擎管理界面展示',
  `EXECUTION_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行实例ID，定位流程中的运行路径',
  `PROC_INST_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID，关联当前或历史流程',
  `TASK_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID，关联办理任务或任务历史',
  `SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `BYTEARRAY_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录',
  `DOUBLE_` double DEFAULT NULL COMMENT '浮点变量值，供流程表达式和任务处理读取',
  `LONG_` bigint DEFAULT NULL COMMENT '整数变量值，供流程表达式和任务处理读取',
  `TEXT_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文本变量值，供流程表达式和任务处理读取',
  `TEXT2_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第二段文本值，保存补充变量或序列化内容',
  `META_INFO_` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型元数据，保存编辑器和发布需要的补充信息',
  PRIMARY KEY (`ID_`),
  KEY `ACT_IDX_RU_VAR_SCOPE_ID_TYPE` (`SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_IDX_RU_VAR_SUB_ID_TYPE` (`SUB_SCOPE_ID_`,`SCOPE_TYPE_`),
  KEY `ACT_FK_VAR_BYTEARRAY` (`BYTEARRAY_ID_`),
  KEY `ACT_IDX_VARIABLE_TASK_ID` (`TASK_ID_`),
  KEY `ACT_FK_VAR_EXE` (`EXECUTION_ID_`),
  KEY `ACT_FK_VAR_PROCINST` (`PROC_INST_ID_`),
  CONSTRAINT `ACT_FK_VAR_BYTEARRAY` FOREIGN KEY (`BYTEARRAY_ID_`) REFERENCES `ACT_GE_BYTEARRAY` (`ID_`),
  CONSTRAINT `ACT_FK_VAR_EXE` FOREIGN KEY (`EXECUTION_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`),
  CONSTRAINT `ACT_FK_VAR_PROCINST` FOREIGN KEY (`PROC_INST_ID_`) REFERENCES `ACT_RU_EXECUTION` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 运行时变量表，保存引擎内部状态和关联数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_login_throttle` (
  `throttle_key` char(66) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '限流键。',
  `failure_count` int NOT NULL DEFAULT '0' COMMENT '失败数量。',
  `window_started_at` datetime(6) NOT NULL COMMENT '窗口开始时间。',
  `blocked_until` datetime(6) DEFAULT NULL COMMENT '阻断截止时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间。',
  PRIMARY KEY (`throttle_key`),
  KEY `idx_auth_login_throttle_updated` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录失败限流表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_refresh_session` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：刷新会话ID，同时写入Access Token的sid声明。',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话所属用户ID。',
  `refresh_token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷新令牌哈希：Refresh Token的SHA-256十六进制摘要。',
  `token_version` bigint NOT NULL COMMENT '令牌版本：创建会话时的用户全局令牌版本。',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间：会话创建时间。',
  `last_used_at` datetime(6) NOT NULL COMMENT '最近使用时间：最近一次成功刷新时间。',
  `idle_expires_at` datetime(6) NOT NULL COMMENT '空闲过期时间：空闲超时时间。',
  `absolute_expires_at` datetime(6) NOT NULL COMMENT '绝对过期时间：会话绝对过期时间。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间：会话撤销时间。',
  `revoked_reason` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话撤销原因。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_refresh_session_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_refresh_session_user` (`user_id`,`revoked_at`),
  KEY `idx_auth_refresh_session_expiry` (`idle_expires_at`,`absolute_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器刷新会话表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_asset_baseline` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置资产迁移基线。 记录某次成功导入发布后，源环境与目标环境的资产版本/内容哈希对照关系， 用于后续导入时判断生产环境是否相对基线发生了本地修改，从而识别冲突或增量更新。',
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产类型。',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务键。',
  `scope_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FULL' COMMENT '范围键。',
  `source_version` int NOT NULL COMMENT '来源版本。',
  `source_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源哈希。',
  `target_version` int DEFAULT NULL COMMENT '目标版本。',
  `target_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标哈希。',
  `import_package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导入配置包ID。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_baseline_scope` (`asset_type`,`business_key`,`scope_key`),
  KEY `idx_asset_baseline_package` (`import_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置资产环境基线表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_environment_mapping` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置环境映射。 记录源环境与目标环境之间的资源编码映射关系(如用户名、角色、部门、数据源等)， 在导入分析阶段用于将源环境的依赖键解析为目标环境对应的键。',
  `source_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型。',
  `source_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源标识。',
  `target_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标标识。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_environment_mapping` (`source_type`,`source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置环境映射表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_export_package` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置导出包。 记录从当前环境打包生成的 wfpack 发布包元数据与二进制内容， 包括包编号、校验和、HMAC 签名、资产数量与下载统计等。',
  `package_no` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置包编号。',
  `migration_tag` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移标记。',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名称。',
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '校验和：内容校验和，用于检测迁移或配置包内容变化；不同表算法以所属服务为准。',
  `signature_value` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '签名值。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY' COMMENT '状态。',
  `asset_count` int NOT NULL DEFAULT '0' COMMENT '资产数量。',
  `package_data` longblob NOT NULL COMMENT '配置包数据。',
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `download_count` int NOT NULL DEFAULT '0' COMMENT '下载数量。',
  `last_download_at` datetime DEFAULT NULL COMMENT '最近下载时间。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_package_no` (`package_no`),
  KEY `idx_export_package_tag` (`migration_tag`),
  KEY `idx_export_package_created` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置导出包表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_export_package_item` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置导出包条目。 记录一个导出包中包含的每个迁移资产及其在导出时使用的选择配置， 用于追溯导出包的资产清单与快照选择范围。',
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置包ID。',
  `asset_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产ID。',
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产类型。',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务键。',
  `source_version` int NOT NULL COMMENT '来源版本。',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容哈希：内容摘要，用于检查配置或快照内容是否一致。',
  `selection_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '选择JSON。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_package_asset` (`package_id`,`asset_id`),
  KEY `idx_export_item_package` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置导出包条目表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_import_item` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置导入条目。 记录导入批次中单个资产的导入全过程状态，包括源/目标版本对照、 比较结果(NEW/CONSISTENT/CONFLICT等)、依赖映射状态、发布状态及异常信息。',
  `import_package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导入配置包ID。',
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产类型。',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务键。',
  `asset_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产名称。',
  `source_version` int NOT NULL COMMENT '来源版本。',
  `source_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源哈希。',
  `target_before_version` int DEFAULT NULL COMMENT '目标之前版本。',
  `target_before_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标之前哈希。',
  `target_after_version` int DEFAULT NULL COMMENT '目标之后版本。',
  `target_after_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标之后哈希。',
  `comparison_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW' COMMENT '比较状态。',
  `mapping_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESOLVED' COMMENT '映射状态。',
  `publish_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '发布状态。',
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '快照JSON。',
  `dependencies_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '依赖集合JSON。',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误消息。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_item_asset` (`import_package_id`,`asset_type`,`business_key`,`source_version`),
  KEY `idx_import_item_package` (`import_package_id`),
  KEY `idx_import_item_compare` (`comparison_status`,`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置导入条目表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_import_package` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置导入批次。 记录从源环境导出的 wfpack 发布包导入到目标环境后的批次信息， 包括原始包内容、校验结果、分析/发布/回滚状态流转以及操作人记录。',
  `package_no` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置包编号。',
  `source_environment` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源环境。',
  `migration_tag` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移标记。',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名称。',
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '校验和：内容校验和，用于检测迁移或配置包内容变化；不同表算法以所属服务为准。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UPLOADED' COMMENT '状态。',
  `validation_report_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '校验报告JSON。',
  `package_data` longblob NOT NULL COMMENT '配置包数据。',
  `imported_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '导入人员。',
  `imported_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间。',
  `published_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人。',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间。',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误消息。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  `signature_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNKNOWN' COMMENT '来源验签状态：导入时记录来源校验结论：VERIFIED 表示签名通过，MISMATCH_CONFIRMED 表示签名不一致但已人工确认来源，UNKNOWN 表示历史记录没有验签证据。与分析、发布状态分别保存，后续查看导入批次时据此说明包的来源可信程度。',
  `signature_confirmed_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源确认人：签名不一致时，由服务端记录确认来源的导入账号；用于追溯谁接受了该配置包。签名通过的正常导入不填写。',
  `signature_confirmed_at` datetime(6) DEFAULT NULL COMMENT '来源确认时间：与来源确认人同时记录，保留人工确认的时间；后续重新分析配置差异不会覆盖这条确认记录。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_checksum` (`checksum`),
  KEY `idx_import_package_tag` (`migration_tag`),
  KEY `idx_import_package_status` (`status`,`imported_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置导入包表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_migration_asset` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置迁移资产。 记录实体/流程配置在每次发布时生成的可迁移快照版本，包含快照内容、内容哈希、 依赖清单、快照完整度(PARTIAL/COMPLETE)以及导出标记与统计，是配置迁移的核心实体。',
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产类型。',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务键。',
  `asset_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产名称。',
  `source_history_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源历史ID。',
  `source_version` int NOT NULL COMMENT '来源版本。',
  `version_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本说明。',
  `migration_tag` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移标记。',
  `mark_for_export` tinyint NOT NULL DEFAULT '1' COMMENT '标记用于导出。',
  `snapshot_completeness` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETE' COMMENT '快照完整性。',
  `snapshot_schema_version` int NOT NULL DEFAULT '1' COMMENT '快照结构版本。',
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '快照JSON。',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容哈希：内容摘要，用于检查配置或快照内容是否一致。',
  `dependencies_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '依赖集合JSON。',
  `dependency_count` int NOT NULL DEFAULT '0' COMMENT '依赖数量。',
  `missing_dependency_count` int NOT NULL DEFAULT '0' COMMENT '缺失依赖数量。',
  `export_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '导出状态。',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间。',
  `published_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人。',
  `last_export_at` datetime DEFAULT NULL COMMENT '最近导出时间。',
  `export_count` int NOT NULL DEFAULT '0' COMMENT '导出数量。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_migration_asset_history` (`asset_type`,`source_history_id`),
  KEY `idx_migration_asset_key` (`asset_type`,`business_key`,`source_version`),
  KEY `idx_migration_asset_tag` (`migration_tag`),
  KEY `idx_migration_asset_export` (`mark_for_export`,`export_status`,`snapshot_completeness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移发布资产表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_migration_asset_dependency` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置迁移资产依赖。 记录某个迁移资产在导入/导出时依赖的其他资产或资源(实体、流程、表单、用户、字典等)， 用于依赖解析、阻断项分析与导出包完整性校验。',
  `asset_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资产ID。',
  `dependency_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '依赖类型。',
  `dependency_key` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '依赖键。',
  `required` tinyint NOT NULL DEFAULT '1' COMMENT '是否必需。',
  `source_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源说明。',
  `dependency_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '依赖扩展JSON文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `source_asset_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源资产类型冗余。',
  `source_business_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源资产稳定标识冗余。',
  `source_version` int DEFAULT NULL COMMENT '来源发布版本。',
  `reference_location` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用在配置中的稳定位置。',
  `dependency_strength` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HARD' COMMENT '依赖强度。',
  `parse_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESOLVED' COMMENT '解析状态。',
  `extracted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '引用抽取时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_asset_dependency` (`asset_id`,`dependency_type`,`dependency_key`),
  KEY `idx_config_dependency_lookup` (`dependency_type`,`dependency_key`),
  KEY `idx_config_reference_source` (`source_asset_type`,`source_business_key`,`source_version`),
  KEY `idx_config_reference_parse` (`parse_status`,`extracted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移资产依赖表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_allowed_origin` (
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权ID。',
  `origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  PRIMARY KEY (`grant_id`,`origin`),
  CONSTRAINT `fk_embed_allowed_origin_grant` FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_allowed_origin_format` CHECK (regexp_like(`origin`,_utf8mb4'^https://[^/?#]+$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入父页面来源表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_application_grant` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图ID。',
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份提供方ID。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；CHECK 枚举：\'ACTIVE\',\'DISABLED\',\'REVOKED\'。',
  `trusted_subject_assertion` tinyint NOT NULL DEFAULT '0' COMMENT '是否信任人员断言；CHECK 枚举：0,1。',
  `revision_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FOLLOW_ACTIVE' COMMENT '修订号模式。',
  `pinned_revision` bigint DEFAULT NULL COMMENT '固定修订号。',
  `capability_ceiling_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力上限JSON。',
  `max_active_sessions_per_user` int NOT NULL DEFAULT '1' COMMENT '每用户最大活跃会话数。',
  `max_session_seconds` int NOT NULL DEFAULT '1800' COMMENT '会话最长秒数。',
  `launch_limit_per_minute` int NOT NULL DEFAULT '60' COMMENT '每分钟启动上限。',
  `runtime_limit_per_minute` int NOT NULL DEFAULT '600' COMMENT '每分钟运行请求上限。',
  `max_concurrency` int NOT NULL DEFAULT '10' COMMENT '最大并发数。',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '过期时间。',
  `lock_version` bigint NOT NULL DEFAULT '1' COMMENT '锁版本。',
  `security_version` bigint NOT NULL DEFAULT '1' COMMENT '安全版本。',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销人。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_grant_application_view` (`application_id`,`view_id`),
  KEY `idx_embed_grant_status_expiry` (`status`,`expires_at`),
  KEY `idx_embed_grant_provider_status` (`identity_provider_id`,`status`),
  KEY `fk_embed_grant_view` (`view_id`),
  CONSTRAINT `fk_embed_grant_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_grant_provider` FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_grant_view` FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_grant_capabilities` CHECK ((json_valid(`capability_ceiling_json`) and (length(`capability_ceiling_json`) <= 65536))),
  CONSTRAINT `chk_embed_grant_limits` CHECK (((`max_active_sessions_per_user` between 1 and 10000) and (`max_session_seconds` between 60 and 86400) and (`launch_limit_per_minute` between 1 and 10000) and (`runtime_limit_per_minute` between 1 and 100000) and (`max_concurrency` between 1 and 1000))),
  CONSTRAINT `chk_embed_grant_revision_mode` CHECK ((((`revision_mode` = _utf8mb4'FOLLOW_ACTIVE') and (`pinned_revision` is null)) or ((`revision_mode` = _utf8mb4'PINNED') and (`pinned_revision` > 0)))),
  CONSTRAINT `chk_embed_grant_revocation` CHECK ((((`status` = _utf8mb4'REVOKED') and (`revoked_by` is not null) and (`revoked_at` is not null)) or ((`status` <> _utf8mb4'REVOKED') and (`revoked_by` is null) and (`revoked_at` is null)))),
  CONSTRAINT `chk_embed_grant_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_embed_grant_trusted_subject` CHECK ((`trusted_subject_assertion` in (0,1))),
  CONSTRAINT `chk_embed_grant_versions` CHECK (((`lock_version` > 0) and (`security_version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入视图应用授权表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_assertion_replay` (
  `provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供方ID。',
  `jti_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'jti摘要。',
  `expires_at` datetime(6) NOT NULL COMMENT '过期时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`provider_id`,`jti_digest`),
  KEY `idx_embed_assertion_replay_expiry` (`expires_at`),
  CONSTRAINT `fk_embed_assertion_replay_provider` FOREIGN KEY (`provider_id`) REFERENCES `embed_identity_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_assertion_replay_digest` CHECK (regexp_like(`jti_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_embed_assertion_replay_expiry` CHECK ((`expires_at` > `create_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员断言防重放表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_external_identity_binding` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份提供方ID。',
  `subject_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体摘要。',
  `subject_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体摘要键版本。',
  `subject_hint` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体提示。',
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Flow用户ID。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；CHECK 枚举：\'ACTIVE\',\'DISABLED\',\'REVOKED\'。',
  `binding_version` bigint NOT NULL DEFAULT '1' COMMENT '绑定版本。',
  `effective_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生效时间。',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '过期时间。',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销人。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_binding_subject` (`application_id`,`identity_provider_id`,`subject_digest`),
  KEY `idx_embed_binding_flow_user` (`flow_user_id`,`status`),
  KEY `idx_embed_binding_expiry` (`status`,`expires_at`),
  KEY `fk_embed_binding_provider` (`identity_provider_id`),
  CONSTRAINT `fk_embed_binding_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_binding_flow_user` FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_binding_provider` FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_binding_digest` CHECK (regexp_like(`subject_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_embed_binding_revocation` CHECK ((((`status` = _utf8mb4'REVOKED') and (`revoked_by` is not null) and (`revoked_at` is not null)) or ((`status` <> _utf8mb4'REVOKED') and (`revoked_by` is null) and (`revoked_at` is null)))),
  CONSTRAINT `chk_embed_binding_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_embed_binding_version` CHECK ((`binding_version` > 0)),
  CONSTRAINT `chk_embed_binding_window` CHECK (((`expires_at` is null) or (`expires_at` > `effective_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部主体用户绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_identity_provider` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称。',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型；CHECK 枚举：\'SIGNED_JWT\',\'TRUSTED_EXTERNAL_ID\'。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；CHECK 枚举：\'ACTIVE\',\'DISABLED\',\'REVOKED\'。',
  `issuer` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发行者。',
  `issuer_uniqueness_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS (coalesce(`issuer`,_utf8mb4'<trusted-external-id>')) STORED COMMENT '发行者唯一性键；数据库生成，表达式见本表实现说明。',
  `subject_namespace` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体命名空间。',
  `audiences_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '受众集合JSON。',
  `algorithms_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '算法集合JSON。',
  `jwks_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JWKS模式；CHECK 枚举：\'STATIC_JWK_SET\',\'REMOTE_JWKS\'。',
  `jwks_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'JWKSJSON。',
  `jwks_url` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JWKSURL。',
  `clock_skew_seconds` int NOT NULL DEFAULT '30' COMMENT '允许时钟偏差秒数。',
  `max_assertion_lifetime_seconds` int NOT NULL DEFAULT '60' COMMENT '断言最长有效秒数。',
  `key_version` bigint NOT NULL DEFAULT '1' COMMENT '键版本。',
  `lock_version` bigint NOT NULL DEFAULT '1' COMMENT '锁版本。',
  `security_version` bigint NOT NULL DEFAULT '1' COMMENT '安全版本。',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销人。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_identity_provider_issuer_ns` (`type`,`issuer_uniqueness_key`,`subject_namespace`),
  KEY `idx_embed_identity_provider_status` (`status`,`update_time`),
  CONSTRAINT `chk_embed_provider_json` CHECK ((json_valid(`audiences_json`) and json_valid(`algorithms_json`) and ((`jwks_json` is null) or json_valid(`jwks_json`)) and (length(`audiences_json`) <= 65536) and (length(`algorithms_json`) <= 65536) and ((`jwks_json` is null) or (length(`jwks_json`) <= 262144)))),
  CONSTRAINT `chk_embed_provider_jwks_mode` CHECK (((`jwks_mode` is null) or (`jwks_mode` in (_utf8mb4'STATIC_JWK_SET',_utf8mb4'REMOTE_JWKS')))),
  CONSTRAINT `chk_embed_provider_material` CHECK ((((`type` = _utf8mb4'SIGNED_JWT') and (`issuer` is not null) and (((`jwks_mode` = _utf8mb4'STATIC_JWK_SET') and (`jwks_json` is not null) and (`jwks_url` is null)) or ((`jwks_mode` = _utf8mb4'REMOTE_JWKS') and (`jwks_json` is null) and (`jwks_url` like _utf8mb4'https://%')))) or ((`type` = _utf8mb4'TRUSTED_EXTERNAL_ID') and (`issuer` is null) and (`jwks_mode` is null) and (`jwks_json` is null) and (`jwks_url` is null)))),
  CONSTRAINT `chk_embed_provider_revocation` CHECK ((((`status` = _utf8mb4'REVOKED') and (`revoked_by` is not null) and (`revoked_at` is not null)) or ((`status` <> _utf8mb4'REVOKED') and (`revoked_by` is null) and (`revoked_at` is null)))),
  CONSTRAINT `chk_embed_provider_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_embed_provider_timing` CHECK (((`clock_skew_seconds` between 0 and 300) and (`max_assertion_lifetime_seconds` between 1 and 300))),
  CONSTRAINT `chk_embed_provider_type` CHECK ((`type` in (_utf8mb4'SIGNED_JWT',_utf8mb4'TRUSTED_EXTERNAL_ID'))),
  CONSTRAINT `chk_embed_provider_versions` CHECK (((`key_version` > 0) and (`lock_version` > 0) and (`security_version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入身份提供方表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_launch` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权ID。',
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图ID。',
  `view_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图发布ID。',
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份提供方ID。',
  `provider_security_version` bigint NOT NULL COMMENT '提供方安全版本。',
  `application_version` bigint NOT NULL COMMENT '应用版本。',
  `grant_security_version` bigint NOT NULL COMMENT '授权安全版本。',
  `view_security_version` bigint NOT NULL COMMENT '视图安全版本。',
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Flow用户ID。',
  `identity_binding_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份绑定ID。',
  `binding_version` bigint NOT NULL COMMENT '绑定版本。',
  `subject_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体摘要。',
  `subject_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体摘要键版本。',
  `parent_origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '父级来源。',
  `channel_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通道ID。',
  `entry_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入口模式；CHECK 枚举：\'LIST\',\'CREATE\' / \'VIEW\',\'EDIT\'。',
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录ID。',
  `context_ciphertext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文密文。',
  `context_cipher_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文加密键版本。',
  `context_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文摘要。',
  `context_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文摘要键版本。',
  `ui_locale` varchar(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN' COMMENT '界面语言区域。',
  `ui_theme` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'light' COMMENT '界面主题；CHECK 枚举：\'light\',\'dark\',\'system\'。',
  `ui_form_presentation` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'seamless' COMMENT '表单展示方式：seamless 或 dialog，默认 seamless；CHECK 枚举：\'seamless\',\'dialog\'；迁移声明排序规则 utf8mb4_bin，标准迁移器完成后再统一为 utf8mb4_unicode_ci。',
  `launch_code_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '启动编码摘要。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ISSUED' COMMENT '状态；CHECK 枚举：\'ISSUED\',\'CONSUMED\',\'EXPIRED\',\'REVOKED\'。',
  `expires_at` datetime(6) NOT NULL COMMENT '过期时间。',
  `consumed_at` datetime(6) DEFAULT NULL COMMENT '消费时间。',
  `consumed_session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消费会话ID。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  `trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '追踪ID。',
  `request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求ID。',
  `source_ip_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源IP摘要。',
  `source_ip_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源IP摘要键版本。',
  `user_agent_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent摘要。',
  `user_agent_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理信息摘要键版本。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_launch_code_digest` (`launch_code_digest`),
  UNIQUE KEY `uk_embed_launch_consumed_session` (`consumed_session_id`),
  KEY `idx_embed_launch_expiry` (`status`,`expires_at`),
  KEY `idx_embed_launch_cleanup` (`status`,`update_time`,`id`),
  KEY `idx_embed_launch_application_view` (`application_id`,`view_id`,`create_time`),
  KEY `idx_embed_launch_binding_status` (`identity_binding_id`,`status`),
  KEY `fk_embed_launch_grant` (`grant_id`),
  KEY `fk_embed_launch_view` (`view_id`),
  KEY `fk_embed_launch_release` (`view_release_id`),
  KEY `fk_embed_launch_provider` (`identity_provider_id`),
  KEY `fk_embed_launch_flow_user` (`flow_user_id`),
  CONSTRAINT `fk_embed_launch_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_binding` FOREIGN KEY (`identity_binding_id`) REFERENCES `embed_external_identity_binding` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_flow_user` FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_grant` FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_provider` FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_release` FOREIGN KEY (`view_release_id`) REFERENCES `embed_view_release` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_view` FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_launch_context` CHECK ((json_valid(`context_ciphertext`) and (length(`context_ciphertext`) <= 65536) and (char_length(`context_cipher_key_version`) > 0) and (char_length(`context_digest_key_version`) > 0) and (char_length(`subject_digest_key_version`) > 0))),
  CONSTRAINT `chk_embed_launch_digest_keys` CHECK (((((`source_ip_digest` is null) and (`source_ip_digest_key_version` is null)) or ((`source_ip_digest` is not null) and (`source_ip_digest_key_version` is not null))) and (((`user_agent_digest` is null) and (`user_agent_digest_key_version` is null)) or ((`user_agent_digest` is not null) and (`user_agent_digest_key_version` is not null))))),
  CONSTRAINT `chk_embed_launch_digests` CHECK ((regexp_like(`subject_digest`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`context_digest`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`launch_code_digest`,_utf8mb4'^[0-9a-f]{64}$') and ((`source_ip_digest` is null) or regexp_like(`source_ip_digest`,_utf8mb4'^[0-9a-f]{64}$')) and ((`user_agent_digest` is null) or regexp_like(`user_agent_digest`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `chk_embed_launch_entry` CHECK ((((`entry_mode` in (_utf8mb4'LIST',_utf8mb4'CREATE')) and (`record_id` is null)) or ((`entry_mode` in (_utf8mb4'VIEW',_utf8mb4'EDIT')) and (`record_id` is not null)))),
  CONSTRAINT `chk_embed_launch_expiry` CHECK ((`expires_at` > `create_time`)),
  CONSTRAINT `chk_embed_launch_form_presentation` CHECK ((`ui_form_presentation` in (_utf8mb4'seamless',_utf8mb4'dialog'))),
  CONSTRAINT `chk_embed_launch_lifecycle` CHECK ((((`status` = _utf8mb4'ISSUED') and (`consumed_at` is null) and (`consumed_session_id` is null) and (`revoked_at` is null)) or ((`status` = _utf8mb4'CONSUMED') and (`consumed_at` is not null) and (`consumed_session_id` is not null) and (`revoked_at` is null)) or ((`status` = _utf8mb4'EXPIRED') and (`consumed_at` is null) and (`consumed_session_id` is null) and (`revoked_at` is null)) or ((`status` = _utf8mb4'REVOKED') and (`consumed_at` is null) and (`consumed_session_id` is null) and (`revoked_at` is not null)))),
  CONSTRAINT `chk_embed_launch_origin` CHECK (regexp_like(`parent_origin`,_utf8mb4'^https://[^/?#]+$')),
  CONSTRAINT `chk_embed_launch_status` CHECK ((`status` in (_utf8mb4'ISSUED',_utf8mb4'CONSUMED',_utf8mb4'EXPIRED',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_embed_launch_theme` CHECK ((`ui_theme` in (_utf8mb4'light',_utf8mb4'dark',_utf8mb4'system'))),
  CONSTRAINT `chk_embed_launch_versions` CHECK (((`provider_security_version` > 0) and (`application_version` >= 0) and (`grant_security_version` > 0) and (`view_security_version` > 0) and (`binding_version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入一次性启动凭据表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_operation_receipt` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `idempotency_record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等记录ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `operation` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作；CHECK 枚举： \'EMBED_RECORD_CREATE\',\'EMBED_RECORD_UPDATE\',\'EMBED_ACTION_EXECUTE\'。',
  `actor_scope_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作主体范围摘要。',
  `view_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图键。',
  `target_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标类型。',
  `target_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标ID。',
  `outcome_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果编码。',
  `record_version` bigint DEFAULT NULL COMMENT '记录版本。',
  `result_summary_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果摘要JSON。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_receipt_idempotency` (`idempotency_record_id`),
  KEY `idx_embed_receipt_cleanup` (`create_time`,`id`),
  KEY `idx_embed_receipt_application` (`application_id`,`operation`,`create_time`),
  KEY `idx_embed_receipt_target` (`target_type`,`target_id`,`create_time`),
  CONSTRAINT `fk_embed_receipt_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_receipt_actor_digest` CHECK (regexp_like(`actor_scope_digest`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_embed_receipt_operation` CHECK ((`operation` in (_utf8mb4'EMBED_RECORD_CREATE',_utf8mb4'EMBED_RECORD_UPDATE',_utf8mb4'EMBED_ACTION_EXECUTE'))),
  CONSTRAINT `chk_embed_receipt_record_version` CHECK (((`record_version` is null) or (`record_version` >= 0))),
  CONSTRAINT `chk_embed_receipt_summary` CHECK ((json_valid(`result_summary_json`) and (length(`result_summary_json`) <= 8192)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入写操作回执表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_session` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `session_token_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话令牌摘要。',
  `launch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '启动ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权ID。',
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图ID。',
  `view_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图发布ID。',
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份提供方ID。',
  `provider_security_version` bigint NOT NULL COMMENT '提供方安全版本。',
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Flow用户ID。',
  `identity_binding_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份绑定ID。',
  `binding_version` bigint NOT NULL COMMENT '绑定版本。',
  `parent_origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '父级来源。',
  `channel_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通道ID。',
  `entry_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入口模式；CHECK 枚举：\'LIST\',\'CREATE\' / \'VIEW\',\'EDIT\'。',
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录ID。',
  `parent_nonce_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '父级随机数摘要。',
  `child_nonce_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子级随机数摘要。',
  `context_ciphertext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文密文。',
  `context_cipher_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文加密键版本。',
  `context_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文摘要。',
  `context_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文摘要键版本。',
  `ui_locale` varchar(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN' COMMENT '界面语言区域。',
  `ui_theme` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'light' COMMENT '界面主题；CHECK 枚举：\'light\',\'dark\',\'system\'。',
  `ui_form_presentation` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'seamless' COMMENT '表单展示方式：seamless 或 dialog，默认 seamless；CHECK 枚举：\'seamless\',\'dialog\'；迁移声明排序规则 utf8mb4_bin，标准迁移器完成后再统一为 utf8mb4_unicode_ci。',
  `capability_snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力快照JSON。',
  `application_version` bigint NOT NULL COMMENT '应用版本。',
  `grant_security_version` bigint NOT NULL COMMENT '授权安全版本。',
  `view_security_version` bigint NOT NULL COMMENT '视图安全版本。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；CHECK 枚举：\'ACTIVE\',\'LOGGED_OUT\',\'EXPIRED\',\'REVOKED\' / \'LOGGED_OUT\',\'EXPIRED\'。',
  `slot_released` tinyint NOT NULL DEFAULT '0' COMMENT '会话名额是否已释放。',
  `slot_released_at` datetime(6) DEFAULT NULL COMMENT '会话名额释放时间。',
  `issued_at` datetime(6) NOT NULL COMMENT '签发时间。',
  `last_seen_at` datetime(6) NOT NULL COMMENT '最近活动时间。',
  `idle_expires_at` datetime(6) NOT NULL COMMENT '空闲过期时间。',
  `absolute_expires_at` datetime(6) NOT NULL COMMENT '绝对过期时间。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  `revoke_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销原因。',
  `source_ip_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源IP摘要。',
  `source_ip_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源IP摘要键版本。',
  `user_agent_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent摘要。',
  `user_agent_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理信息摘要键版本。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_session_token_digest` (`session_token_digest`),
  UNIQUE KEY `uk_embed_session_launch` (`launch_id`),
  KEY `idx_embed_session_expiry` (`status`,`idle_expires_at`,`absolute_expires_at`),
  KEY `idx_embed_session_counter_reconcile` (`status`,`slot_released`,`grant_id`,`flow_user_id`),
  KEY `idx_embed_session_terminal_cleanup` (`status`,`slot_released_at`,`id`),
  KEY `idx_embed_session_application` (`application_id`,`status`),
  KEY `idx_embed_session_view` (`view_id`,`status`),
  KEY `idx_embed_session_user` (`flow_user_id`,`status`),
  KEY `fk_embed_session_grant` (`grant_id`),
  KEY `fk_embed_session_release` (`view_release_id`),
  KEY `fk_embed_session_provider` (`identity_provider_id`),
  KEY `fk_embed_session_binding` (`identity_binding_id`),
  CONSTRAINT `fk_embed_session_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_binding` FOREIGN KEY (`identity_binding_id`) REFERENCES `embed_external_identity_binding` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_flow_user` FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_grant` FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_launch` FOREIGN KEY (`launch_id`) REFERENCES `embed_launch` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_provider` FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_release` FOREIGN KEY (`view_release_id`) REFERENCES `embed_view_release` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_view` FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_session_context` CHECK ((json_valid(`context_ciphertext`) and json_valid(`capability_snapshot_json`) and (length(`context_ciphertext`) <= 65536) and (length(`capability_snapshot_json`) <= 65536) and (char_length(`context_cipher_key_version`) > 0) and (char_length(`context_digest_key_version`) > 0))),
  CONSTRAINT `chk_embed_session_digest_keys` CHECK (((((`source_ip_digest` is null) and (`source_ip_digest_key_version` is null)) or ((`source_ip_digest` is not null) and (`source_ip_digest_key_version` is not null))) and (((`user_agent_digest` is null) and (`user_agent_digest_key_version` is null)) or ((`user_agent_digest` is not null) and (`user_agent_digest_key_version` is not null))))),
  CONSTRAINT `chk_embed_session_digests` CHECK ((regexp_like(`session_token_digest`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`parent_nonce_digest`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`child_nonce_digest`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`context_digest`,_utf8mb4'^[0-9a-f]{64}$') and ((`source_ip_digest` is null) or regexp_like(`source_ip_digest`,_utf8mb4'^[0-9a-f]{64}$')) and ((`user_agent_digest` is null) or regexp_like(`user_agent_digest`,_utf8mb4'^[0-9a-f]{64}$')))),
  CONSTRAINT `chk_embed_session_entry` CHECK ((((`entry_mode` in (_utf8mb4'LIST',_utf8mb4'CREATE')) and (`record_id` is null)) or ((`entry_mode` in (_utf8mb4'VIEW',_utf8mb4'EDIT')) and (`record_id` is not null)))),
  CONSTRAINT `chk_embed_session_form_presentation` CHECK ((`ui_form_presentation` in (_utf8mb4'seamless',_utf8mb4'dialog'))),
  CONSTRAINT `chk_embed_session_origin` CHECK (regexp_like(`parent_origin`,_utf8mb4'^https://[^/?#]+$')),
  CONSTRAINT `chk_embed_session_slot` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`slot_released` = 0) and (`slot_released_at` is null)) or ((`status` in (_utf8mb4'LOGGED_OUT',_utf8mb4'EXPIRED')) and (`slot_released` = 1) and (`slot_released_at` is not null) and (`revoked_at` is null) and (`revoke_reason` is null)) or ((`status` = _utf8mb4'REVOKED') and (`slot_released` = 1) and (`slot_released_at` is not null) and (`revoked_at` is not null) and (`revoke_reason` is not null)))),
  CONSTRAINT `chk_embed_session_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'LOGGED_OUT',_utf8mb4'EXPIRED',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_embed_session_theme` CHECK ((`ui_theme` in (_utf8mb4'light',_utf8mb4'dark',_utf8mb4'system'))),
  CONSTRAINT `chk_embed_session_time_window` CHECK (((`issued_at` <= `last_seen_at`) and (`issued_at` < `idle_expires_at`) and (`idle_expires_at` <= `absolute_expires_at`))),
  CONSTRAINT `chk_embed_session_versions` CHECK (((`provider_security_version` > 0) and (`application_version` >= 0) and (`grant_security_version` > 0) and (`view_security_version` > 0) and (`binding_version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入运行会话表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_session_counter` (
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权ID。',
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Flow用户ID。',
  `active_count` int NOT NULL DEFAULT '0' COMMENT '活跃数量。',
  `lock_version` bigint NOT NULL DEFAULT '0' COMMENT '锁版本。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`grant_id`,`flow_user_id`),
  KEY `fk_embed_session_counter_user` (`flow_user_id`),
  CONSTRAINT `fk_embed_session_counter_grant` FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_counter_user` FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_session_counter_count` CHECK ((`active_count` >= 0)),
  CONSTRAINT `chk_embed_session_counter_lock` CHECK ((`lock_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入活跃会话计数表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_view` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `view_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图键。',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称。',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `surface_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '界面类型；CHECK 枚举：\'LIST\',\'FORM\'。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态；CHECK 枚举：\'DRAFT\',\'ACTIVE\',\'DISABLED\',\'RETIRED\'。',
  `draft_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草稿配置JSON。',
  `draft_revision` bigint NOT NULL DEFAULT '1' COMMENT '草稿修订号。',
  `published_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布发布ID。',
  `lock_version` bigint NOT NULL DEFAULT '1' COMMENT '锁版本。',
  `security_version` bigint NOT NULL DEFAULT '1' COMMENT '安全版本。',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_view_key` (`view_key`),
  KEY `idx_embed_view_status` (`status`,`update_time`),
  CONSTRAINT `chk_embed_view_draft_json` CHECK ((json_valid(`draft_config_json`) and (length(`draft_config_json`) <= 262144))),
  CONSTRAINT `chk_embed_view_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'RETIRED'))),
  CONSTRAINT `chk_embed_view_surface` CHECK ((`surface_type` in (_utf8mb4'LIST',_utf8mb4'FORM'))),
  CONSTRAINT `chk_embed_view_versions` CHECK (((`draft_revision` > 0) and (`lock_version` > 0) and (`security_version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入视图草稿表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `embed_view_release` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图ID。',
  `revision` bigint NOT NULL COMMENT '修订号。',
  `surface_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '界面类型；CHECK 枚举：\'LIST\',\'FORM\'。',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `list_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表键。',
  `default_form_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认表单ID。',
  `list_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表发布ID。',
  `list_release_version` bigint DEFAULT NULL COMMENT '列表发布版本。',
  `form_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '表单发布ID。',
  `form_release_version` bigint DEFAULT NULL COMMENT '表单发布版本。',
  `entry_modes_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入口模式JSON。',
  `capabilities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力集合JSON。',
  `field_policy_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段策略JSON。',
  `action_policy_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作策略JSON。',
  `context_schema_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文结构JSON。',
  `context_bindings_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上下文绑定集合JSON。',
  `ui_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '界面配置JSON。',
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置JSON。',
  `config_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置哈希。',
  `release_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布说明。',
  `published_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布人。',
  `published_at` datetime(6) NOT NULL COMMENT '发布时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_view_release_revision` (`view_id`,`revision`),
  KEY `idx_embed_view_release_published` (`view_id`,`published_at`),
  CONSTRAINT `fk_embed_view_release_view` FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_release_form_pair` CHECK ((((`form_release_id` is null) and (`form_release_version` is null)) or ((`form_release_id` is not null) and (`form_release_version` > 0)))),
  CONSTRAINT `chk_embed_release_hash` CHECK (regexp_like(`config_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_embed_release_json` CHECK ((json_valid(`entry_modes_json`) and json_valid(`capabilities_json`) and json_valid(`field_policy_json`) and json_valid(`action_policy_json`) and json_valid(`context_schema_json`) and json_valid(`context_bindings_json`) and json_valid(`ui_config_json`) and json_valid(`config_json`) and (length(`entry_modes_json`) <= 65536) and (length(`capabilities_json`) <= 65536) and (length(`field_policy_json`) <= 262144) and (length(`action_policy_json`) <= 262144) and (length(`context_schema_json`) <= 262144) and (length(`context_bindings_json`) <= 262144) and (length(`ui_config_json`) <= 262144) and (length(`config_json`) <= 262144))),
  CONSTRAINT `chk_embed_release_list_pair` CHECK ((((`list_release_id` is null) and (`list_release_version` is null)) or ((`list_release_id` is not null) and (`list_release_version` > 0)))),
  CONSTRAINT `chk_embed_release_revision` CHECK ((`revision` > 0)),
  CONSTRAINT `chk_embed_release_surface` CHECK ((`surface_type` in (_utf8mb4'LIST',_utf8mb4'FORM'))),
  CONSTRAINT `chk_embed_release_target` CHECK ((((`surface_type` = _utf8mb4'LIST') and (`list_key` is not null) and (`list_release_id` is not null) and (`list_release_version` is not null) and ((`default_form_id` is null) or (`form_release_id` is not null))) or ((`surface_type` = _utf8mb4'FORM') and (`list_key` is null) and (`list_release_id` is null) and (`list_release_version` is null) and (`form_release_id` is not null) and (`form_release_version` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入视图发布快照表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_code_rule` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `prefix` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '编码前缀，如：CG、DD。',
  `date_format` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'yyyyMMdd' COMMENT '日期格式，如：yyyyMMdd、yyyy-MM-dd。',
  `seq_length` int DEFAULT '6' COMMENT '序号长度：序列号位数，如：6表示000001。',
  `seq_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DAY' COMMENT '序号重置类型：序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置。',
  `current_seq` int DEFAULT '0' COMMENT '当前序号：当前序列号值。',
  `seq_date` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '序号所属日期：当前序列号对应的日期（用于判断重置）。',
  `example` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '编码示例。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体业务编码规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供实体定义表中的记录关联；由数据库分配',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `entity_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体名称。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：实体描述。',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联流程定义ID。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `table_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据库表名。',
  `lifecycle_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDALONE' COMMENT '实体生命周期模式：STANDALONE/WORKFLOW。',
  `storage_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DYNAMIC' COMMENT '存储模式：DYNAMIC/SYSTEM。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人：更新人。',
  `team_visibility_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '团队可见性开关：是否允许数据参与团队查看记录；团队可见性的旧开关不再参与权限引擎计算；当前改用列表 TEAM 规则，字段仍被保存和发布快照复制。',
  `team_visibility_level` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADDITIVE' COMMENT '团队可见性级别：参与团队权限级别：ADDITIVE/OVERRIDE_SCOPE/ABSOLUTE；团队可见性的旧开关不再参与权限引擎计算；当前改用列表 TEAM 规则，字段仍被保存和发布快照复制。',
  `active_process_definition_key` bigint GENERATED ALWAYS AS ((case when ((coalesce(`deleted`,0) = 0) and regexp_like(trim(`process_definition_id`),_utf8mb4'^[0-9]+$') and (nullif(trim(leading _utf8mb4'0' from trim(`process_definition_id`)),_utf8mb4'') is not null) and ((char_length(trim(leading _utf8mb4'0' from trim(`process_definition_id`))) < 19) or ((char_length(trim(leading _utf8mb4'0' from trim(`process_definition_id`))) = 19) and (trim(leading _utf8mb4'0' from trim(`process_definition_id`)) <= _utf8mb4'9223372036854775807')))) then cast(trim(leading _utf8mb4'0' from trim(`process_definition_id`)) as unsigned) else NULL end)) VIRTUAL COMMENT '有效流程绑定索引键；数据库生成，表达式见本表实现说明。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `entity_code` (`entity_code`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_status` (`status`),
  KEY `idx_lifecycle_mode` (`lifecycle_mode`),
  KEY `idx_storage_mode` (`storage_mode`),
  KEY `idx_entity_definition_process_binding` (`active_process_definition_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2099424350330544131 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供实体字段定义表中的记录关联；由数据库分配',
  `entity_id` bigint NOT NULL COMMENT '所属实体ID。',
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码。',
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称。',
  `field_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段类型。',
  `db_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据库字段类型。',
  `field_length` int DEFAULT NULL COMMENT '字段长度。',
  `is_required` tinyint(1) DEFAULT '0' COMMENT '是否必填。',
  `is_unique` tinyint(1) DEFAULT '0' COMMENT '是否唯一。',
  `default_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认值。',
  `options_json` text COLLATE utf8mb4_unicode_ci COMMENT '选项配置JSON。',
  `validate_rules` text COLLATE utf8mb4_unicode_ci COMMENT '验证规则JSON。',
  `sort_order` int DEFAULT '0' COMMENT '排序号：排序顺序。',
  `is_system` tinyint(1) DEFAULT '0' COMMENT '是否系统字段：0-否 1-是（系统自动添加的字段，不可删除）。',
  `is_published` tinyint DEFAULT '0' COMMENT '是否已发布到数据库表。',
  `editable` tinyint(1) DEFAULT '1' COMMENT '是否可编辑：0-否 1-是。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `field_precision` int DEFAULT NULL COMMENT '小数位数（精度）。',
  `db_column_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据库列名（下划线命名）。',
  `file_types` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）。',
  `file_max_size` int DEFAULT NULL COMMENT '文件大小限制（MB，用于附件类型）。',
  `file_max_count` int DEFAULT NULL COMMENT '文件数量限制（用于附件类型）。',
  `ref_entity_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）。',
  `ref_entity_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体ID。',
  `ref_field_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联字段编码。',
  `ref_list_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '子列表或实体引用默认使用的已发布列表编码。',
  `field_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧字段编码（兼容保留）；旧字段编码；迁移注释明确为兼容保留，不是该行主键。',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '绑定的系统代码表编码。',
  `value_storage` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SCALAR' COMMENT '值存储方式：字段值存储：SCALAR/MULTI_TABLE。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field` (`entity_id`,`field_code`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_field_code` (`field_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2100452095680323873 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体字段定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field_file_item` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `field_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联字段ID（entity_field.id）。',
  `item_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '附件项不可变业务标识。',
  `item_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '附件项名称。',
  `name_aliases` longtext COLLATE utf8mb4_unicode_ci COMMENT '历史附件项名称 JSON 数组；用于历史附件项名称归并到稳定 item_key，仍有业务用途。',
  `is_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填：该附件项是否必填。',
  `file_types` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '允许的文件类型。',
  `max_size` int DEFAULT NULL COMMENT '单文件大小限制（MB）。',
  `max_count` int DEFAULT NULL COMMENT '文件数量限制。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field_file_item_key` (`field_id`,`item_key`),
  KEY `idx_field_id` (`field_id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体字段附件项表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field_option` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `field_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属字段ID。',
  `option_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项值（提交时存储的实际值）。',
  `option_label` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项标签（界面显示文案）。',
  `style_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '选项样式类型（如 primary/success/danger 等）。',
  `disabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否禁用（true-禁用不可选）。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `option_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '选项扩展JSON文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field_option` (`field_id`,`option_value`),
  KEY `idx_entity_field_option_sort` (`field_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体字段静态选项表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：表单ID。',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体ID。',
  `form_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单名称。',
  `form_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单标识。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '说明：描述。',
  `layout_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'vertical' COMMENT '布局类型：vertical-垂直 horizontal-水平 grid-网格。',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用 1-启用。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：删除标志。',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认表单。',
  `custom_component` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义表单组件注册名。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人：更新人。',
  `view_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '表单视图配置JSON：布局、自定义组件参数。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：草稿元数据修订号。',
  `active_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前激活发布快照ID。',
  `draft_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前草稿内容哈希。',
  `custom_component_version` int DEFAULT NULL COMMENT '自定义整页表单组件锁定版本。',
  `custom_component_snapshot_version` int DEFAULT NULL COMMENT '自定义整页表单配置快照版本。',
  `data_source_bindings_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '数据源绑定文档：表单级统一数据源绑定JSON文档。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_form_key` (`entity_id`,`form_key`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体表单定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form_node` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：稳定节点ID。',
  `form_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单ID。',
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父节点ID。',
  `node_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单内稳定节点编码。',
  `active_node_key` varchar(100) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`deleted` = 0) then `node_key` else NULL end)) STORED COMMENT '仅活动节点参与表单内节点编码唯一约束；数据库生成，表达式见本表实现说明。',
  `node_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型。',
  `binding_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '绑定类型。',
  `binding_ref` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字段、关系或上下文引用。',
  `props_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '节点显式属性JSON文档。',
  `rules_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '校验、显隐和权限规则JSON文档。',
  `data_source_bindings_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '数据源绑定文档：节点数据源绑定JSON文档。',
  `legacy_props_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '无法识别的历史属性JSON文档；仍保存历史或当前节点类型不适用的属性，并参与恢复；不是已废弃的空列。',
  `order_key` bigint NOT NULL DEFAULT '1000000' COMMENT '稀疏排序键。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：节点草稿修订号。',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源模板ID。',
  `template_version` int DEFAULT NULL COMMENT '锁定模板版本。',
  `local_overrides_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '模板实例本地覆盖JSON文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。',
  `component_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点扩展组件注册名。',
  `component_version` int DEFAULT NULL COMMENT '节点扩展组件锁定版本。',
  `snapshot_version` int DEFAULT NULL COMMENT '节点扩展配置快照版本。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_form_node_active_key` (`form_id`,`active_node_key`),
  KEY `idx_entity_form_node_tree` (`form_id`,`parent_id`,`order_key`,`deleted`),
  KEY `idx_entity_form_node_binding` (`form_id`,`binding_type`,`binding_ref`,`deleted`),
  KEY `idx_entity_form_node_form_key` (`form_id`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体表单递归节点表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form_unique_claim` (
  `constraint_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '唯一约束命名空间，格式 FORM:{formId}:{snapshotIdentity}:{ruleId}。',
  `value_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规范化值的 SHA-256。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `form_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则所属表单ID。',
  `rule_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '跨发布版本稳定的规则ID。',
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则字段编码。',
  `normalized_value` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '截断后的规范化值，仅用于排障。',
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '占位所属业务记录ID。',
  `release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次维护占位的表单发布ID，仅用于审计。',
  `release_version` int DEFAULT NULL COMMENT '最近一次维护占位的表单发布版本，仅用于审计。',
  `effective_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则实际来源发布ID；热修复时为热修复发布ID。',
  `effective_content_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则实际有效快照哈希，仅用于审计和完整性追踪。',
  `hotfix_target_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热修复目标ID；非热修复为空。',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`constraint_key`,`value_hash`),
  UNIQUE KEY `uk_form_unique_claim_record` (`constraint_key`,`record_id`),
  KEY `idx_form_unique_claim_record` (`entity_code`,`record_id`),
  KEY `idx_form_unique_claim_form_rule` (`form_id`,`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单唯一值原子占位表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form_unique_value_gate` (
  `scope_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定值锁作用域，格式 ENTITY:{entityCode}:{fieldCode}。',
  `value_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规范化值的 SHA-256。',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`scope_key`,`value_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单唯一值事务门闩表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_action` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表配置ID。',
  `position` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职务。',
  `button_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定按钮编码。',
  `button_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'built-in' COMMENT '按钮类型。',
  `button_label` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '按钮名称。',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标。',
  `style_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '按钮样式。',
  `link_mode` tinyint NOT NULL DEFAULT '0' COMMENT '是否链接按钮。',
  `custom_mode` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义模式。',
  `handler_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器或组件编码。',
  `permission_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '功能权限码。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `unavailable_behavior` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '历史不可用行为：历史字段；v2 显示/启用规则不再读取，待数据库清理时移除。',
  `action_params_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '按钮扩展参数JSON文档。',
  `availability_rule_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '按钮适用条件JSON文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：按钮草稿修订号。',
  `order_key` bigint NOT NULL DEFAULT '1000000' COMMENT '稀疏排序键。',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源模板ID。',
  `template_version` int DEFAULT NULL COMMENT '锁定模板版本。',
  `local_overrides_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '模板实例本地覆盖JSON文档。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_action` (`list_config_id`,`position`,`button_key`,`deleted`),
  KEY `idx_entity_list_action_runtime` (`list_config_id`,`position`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表按钮配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表配置 ID：主键。普通创建由 MyBatis-Plus `ASSIGN_UUID` 生成，不是数据库自增 ID',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属实体 ID：逻辑关联 `entity_definition.id`。注意目标主键是 `bigint`，本表以字符串保存',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属实体编码：冗余保存实体编码，服务于按编码定位列表及运行时权限解析；业务上应与 `entity_id` 指向同一实体',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表标识：实体内的稳定编码，如 `default`、`picker`。保存校验格式为 `[A-Za-z][A-Za-z0-9_-]{0,99}`',
  `list_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表名称：面向管理员与使用者的显示名称，可修改。数据库非空约束不等于禁止空字符串',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表说明：说明列表用途与适用范围',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认列表：`1` 为默认，`0` 为非默认。未指定列表标识时优先选择默认列表；当前未强制一个实体只能有一个默认列表',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：`0` 为正常，`1` 为已删除；MyBatis-Plus 使用 `@TableLogic`，常规查询筛选 `deleted = 0`',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：数据库提供插入默认时间，普通保存流程也会设置创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间：含 `ON UPDATE CURRENT_TIMESTAMP`，应用更新时也会设置；不能当作发布时间或修订号',
  `custom_component` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义列表组件：前端已注册的整页列表组件名；为空时使用平台动态列表。组件名是注册标识，不是可执行脚本',
  `toolbar_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '工具栏按钮配置：JSON 数组，保存工具栏按钮配置；与按钮关系表的读取优先级见 2.4.2',
  `row_action_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '行内操作配置：JSON 数组，保存每行操作按钮配置；与按钮关系表的读取优先级见 2.4.2',
  `view_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '列表视图配置：JSON 对象，保存查询区、表格、分页与自定义组件参数；具体列定义在字段子表',
  `data_scope_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INHERIT' COMMENT '数据范围模式：允许 `INHERIT`、`NARROW`、`OVERRIDE`；保留的模式标识。当前执行语义的限制见 2.4.4，不能仅凭字段名认定存在三套范围合并算法',
  `unbound_scope_policy` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DENY_ALL' COMMENT '未绑定允许规则时的策略：没有已启用且在有效期内的 ALLOW 绑定时，采用拒绝全部、本人数据或显式全量可见的默认范围',
  `scope_enforcement_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENFORCE' COMMENT '默认策略执行阶段：`OBSERVE` 为存量观察期；`ENFORCE` 为执行安全默认策略',
  `scope_default_confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '全量可见是否已确认：`1` 表示已显式确认；执行阶段下 `EXPLICIT_ALL` 需要该确认',
  `scope_default_confirmed_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '全量可见确认人：记录确认操作的当前用户 ID；不代表本条列表配置的创建人',
  `scope_default_confirmed_at` datetime DEFAULT NULL COMMENT '全量可见确认时间：记录显式确认发生的时间',
  `scope_default_confirmation_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '全量可见确认原因：记录放开数据范围的业务原因；首次确认时应用要求至少 5 个字符',
  `access_permission_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表访问权限码：控制进入列表的权限；空值回退为 `entity:{entity_code}:list`。访问权限与可见记录范围分别校验',
  `allowed_scenes` longtext COLLATE utf8mb4_unicode_ci COMMENT '历史场景配置：功能已移除，应用不再读写',
  `selection_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '选数配置：JSON 对象，描述是否允许选数、主值字段及选中记录的返回映射',
  `fixed_filter_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '固定查询条件：JSON 对象。无论是否绑定数据范围规则均生效；与权限范围取交集，省略 `_op` 时按 `EQ`，用户筛选和自定义查询不能放宽条件',
  `query_provider_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '查询提供者编码：已注册 `EntityListDataProvider` 的编码，用于自定义列表查询；不能与接口扩展查询同时配置',
  `published_version` int NOT NULL DEFAULT '0' COMMENT '当前界面发布版本号：`0` 为尚未发布；发布后对应当前激活的 LIST 快照版本。不是草稿修订号，也不是数据权限发布版本',
  `revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号：列表编辑的并发控制版本。普通元数据更新校验 `expectedRevision`，成功后递增；字段、按钮变更也会触碰主表修订号',
  `active_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前激活快照 ID：逻辑关联 `ui_config_release.id`；对应快照必须属于本列表且为 LIST 类型。普通运行入口要求其与当前 ACTIVE 快照一致',
  `draft_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '草稿内容哈希：保存规范化快照的 SHA-256 十六进制哈希。草稿编辑通常将其清空，发布时写入；需要比较完整草稿内容，不能只凭此字段判断有无变更',
  `query_interface_extension_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '查询接口扩展 ID：历史查询槽位，逻辑关联 `ui_extension_definition.id`；V094 将存量草稿迁入 `LIST_LOAD` 替代步骤后清空，保留字段以读取旧发布',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_key` (`entity_id`,`list_key`,`deleted`),
  KEY `idx_entity_id` (`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_field` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属列表配置ID。',
  `field_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体字段ID（关联entity_field）。',
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码。',
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称（快照）。',
  `sort_order` int DEFAULT '0' COMMENT '排序号：列排序号；当前主要按 order_key 排序，本列仍作为次级排序和兼容值。',
  `width` int DEFAULT '0' COMMENT '列宽度（0表示自适应）。',
  `show_in_list` tinyint DEFAULT '1' COMMENT '是否显示在列表。',
  `is_query` tinyint DEFAULT '1' COMMENT '是否作为查询条件。',
  `query_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'LIKE' COMMENT '查询方式：EQ/NE/LIKE/GT/LT/BETWEEN/IN。',
  `align` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'left' COMMENT '对齐方式：left/center/right。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `data_source_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'ENTITY_FIELD' COMMENT '数据源类型：ENTITY_FIELD(实体字段)/REFERENCE(关联查询)/AGGREGATE(聚合统计)/CUSTOM_PROVIDER(自定义处理器)。',
  `data_source_config` text COLLATE utf8mb4_unicode_ci COMMENT '数据源配置JSON。',
  `render_component` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端渲染组件名。',
  `formatter` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '简单格式化表达式（如 yyyy-MM-dd、#0.00）。',
  `column_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '列展示配置JSON。',
  `query_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '查询组件配置JSON。',
  `render_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '单元格渲染配置JSON。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：字段草稿修订号。',
  `order_key` bigint NOT NULL DEFAULT '1000000' COMMENT '稀疏排序键。',
  `interface_extension_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列接口扩展 ID：逻辑关联一条可用于 `LIST_COLUMN` 的 `INTERFACE` 扩展；无需再保存操作编码。',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源模板ID。',
  `template_version` int DEFAULT NULL COMMENT '锁定模板版本。',
  `local_overrides_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '模板实例本地覆盖JSON文档。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_field` (`list_config_id`,`field_id`,`deleted`),
  KEY `idx_list_config_id` (`list_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表字段配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scene` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属列表配置ID。',
  `scene_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '场景编码（如 pc/mobile/picker 等）。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：场景草稿修订号。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scene` (`list_config_id`,`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表场景配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_audit_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表编码。',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作或被校验用户。',
  `operation` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作。',
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果。',
  `detail_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '结构化详情。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：记录时间。',
  PRIMARY KEY (`id`),
  KEY `idx_entity_list_scope_audit` (`entity_code`,`list_key`,`create_time`),
  KEY `idx_entity_list_scope_audit_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表数据范围审计表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `policy_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据范围方案ID。',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表编码，空表示实体默认范围。',
  `match_config` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用用户结构化条件。',
  `rule_effect` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALLOW' COMMENT '规则效果。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `effective_start_time` datetime DEFAULT NULL COMMENT '生效时间。',
  `effective_end_time` datetime DEFAULT NULL COMMENT '失效时间。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除。',
  PRIMARY KEY (`id`),
  KEY `idx_entity_list_scope_binding_runtime` (`entity_code`,`list_key`,`enabled`,`deleted`),
  KEY `idx_entity_list_scope_binding_policy` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表数据范围绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_delegation` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体编码，空表示全部实体。',
  `from_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '委托方用户ID。',
  `to_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '受托方用户ID。',
  `delegate_scope` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PERSONAL' COMMENT '委派范围。',
  `policy_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '指定方案ID。',
  `delegate_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '附加结构化条件。',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间。',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除。',
  PRIMARY KEY (`id`),
  KEY `idx_entity_list_scope_delegate_runtime` (`to_user_id`,`entity_code`,`enabled`,`deleted`),
  KEY `idx_entity_list_scope_delegate_from` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表数据范围委派表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_policy` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `policy_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案稳定编码。',
  `policy_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案名称。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：方案说明。',
  `preset_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内置模板编码。',
  `filter_config` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结构化数据条件。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号：配置版本。',
  `review_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要人工复核：旧复杂规则是否需要人工确认。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scope_policy` (`entity_code`,`policy_key`,`deleted`),
  KEY `idx_entity_list_scope_policy_runtime` (`entity_code`,`status`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表数据范围方案表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_release` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `version` int NOT NULL COMMENT '版本号：发布版本。',
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案、绑定和列表模式完整快照。',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容哈希：内容摘要，用于检查配置或快照内容是否一致。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：发布说明。',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人。',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scope_release` (`entity_code`,`version`),
  KEY `idx_entity_list_scope_release_active` (`entity_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表数据范围发布表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_mutation_receipt` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：变更回执ID。',
  `idempotency_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键：全局幂等键。',
  `command_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更命令摘要。',
  `operation_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作ID。',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录ID。',
  `operation_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态。',
  `result_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '首次成功执行结果JSON。',
  `version_no` int DEFAULT NULL COMMENT '版本编号。',
  `version_scenario_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本场景编码。',
  `changed` tinyint NOT NULL DEFAULT '0' COMMENT '是否发生变化。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_mutation_receipt_key` (`idempotency_key`),
  KEY `idx_entity_mutation_receipt_record` (`entity_code`,`record_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体变更幂等回执表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_process_link` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `entity_record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体记录ID。',
  `generation` int NOT NULL DEFAULT '1' COMMENT '代次。',
  `process_definition_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义键。',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID。',
  `state` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理状态：关联状态：PENDING 等待发起、ACTIVE 已关联运行实例、ENDED 已结束；与 end_type 配合区分生命周期和结束原因。',
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求ID。',
  `entity_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体状态。',
  `ended_at` datetime(6) DEFAULT NULL COMMENT '结束时间。',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '版本号。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间。',
  `end_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程结束类型：在流程结束同步时记录 COMPLETED（正常结束）、TERMINATED（终止）或 WITHDRAWN（撤回），用于区分结束原因。关联状态统一为 ENDED，业务审批是否通过仍看业务状态，不能仅凭 COMPLETED 判断。未结束或历史原因未确认时可为空。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_process_generation` (`entity_code`,`entity_record_id`,`generation`),
  UNIQUE KEY `uk_entity_process_request` (`request_id`),
  UNIQUE KEY `uk_entity_process_instance` (`process_instance_id`),
  KEY `idx_entity_process_state` (`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体与流程实例关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_publish_history` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体定义ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `entity_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体名称。',
  `version` int NOT NULL COMMENT '版本号。',
  `version_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本说明。',
  `fields_snapshot` longtext COLLATE utf8mb4_unicode_ci COMMENT '字段定义快照JSON。',
  `relations_snapshot` longtext COLLATE utf8mb4_unicode_ci COMMENT '发布时实体关系定义快照JSON，NULL为旧发布。',
  `table_ddl` longtext COLLATE utf8mb4_unicode_ci COMMENT '表结构DDL。',
  `publish_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'CREATE' COMMENT '发布类型：CREATE首次创建/ALTER修改结构。',
  `changes_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更内容描述。',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间。',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人ID。',
  `published_by_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人名称：发布人姓名。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/ROLLBACK已回滚。',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布时绑定流程定义ID。',
  `lifecycle_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDALONE' COMMENT '发布时实体生命周期模式。',
  `team_visibility_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '团队可见性开关：发布时是否允许数据参与团队查看记录；保留旧发布的团队可见性信息，当前权限计算不依赖这两个开关。',
  `team_visibility_level` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADDITIVE' COMMENT '团队可见性级别：发布时参与团队权限级别；保留旧发布的团队可见性信息，当前权限计算不依赖这两个开关。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version` (`entity_id`,`version`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_publish_type` (`publish_type`),
  KEY `idx_status` (`status`),
  KEY `idx_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体发布历史表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_record_version` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：业务版本ID。',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID。',
  `version_no` int NOT NULL COMMENT '同一记录从1递增。',
  `version_title` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本标题。',
  `scenario_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '场景编码。',
  `scenario_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '场景名称。',
  `operation_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型。',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型。',
  `source_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源ID。',
  `business_intent_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务意图编码。',
  `business_intent_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务意图名称。',
  `source_entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源实体编码。',
  `source_record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源记录ID。',
  `process_definition_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID。',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID。',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID。',
  `operator_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID。',
  `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人名称。',
  `business_trace_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务追踪键。',
  `idempotency_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。',
  `entity_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体发布ID。',
  `entity_release_version` int DEFAULT NULL COMMENT '实体发布版本。',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '快照契约版本：1/2。',
  `config_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧版本策略发布ID：仅供滚动发布期间的旧 Pod 写入；业务快照不再依赖此值。',
  `config_release_version` int DEFAULT NULL COMMENT '旧版本策略发布版本：仅供滚动发布期间的旧 Pod 写入；新代码不使用。',
  `data_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始业务数据摘要。',
  `presentation_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '冻结中文展示语义摘要。',
  `scope_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '固化范围摘要。',
  `request_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '幂等请求摘要。',
  `dataset_count` int NOT NULL DEFAULT '0' COMMENT '关系数据集数量。',
  `snapshot_row_count` int NOT NULL DEFAULT '1' COMMENT '根记录与关系记录总数。',
  `snapshot_size_bytes` bigint NOT NULL DEFAULT '0' COMMENT 'V2快照序列化字节数。',
  `completeness` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETE' COMMENT '完整性：COMPLETE；V2禁止截断。',
  `snapshot_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '快照哈希。',
  `snapshot_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '快照文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_record_version_no` (`entity_code`,`record_id`,`version_no`),
  UNIQUE KEY `uk_entity_record_version_idempotent` (`entity_code`,`record_id`,`idempotency_key`),
  KEY `idx_entity_record_version_time` (`entity_code`,`record_id`,`create_time`),
  KEY `idx_entity_record_version_process` (`process_instance_id`),
  KEY `idx_entity_record_version_release` (`config_release_id`),
  KEY `idx_entity_record_version_schema` (`schema_version`,`create_time`),
  CONSTRAINT `fk_entity_record_version_config_release` FOREIGN KEY (`config_release_id`) REFERENCES `entity_version_config_release` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体记录版本表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_record_version_counter` (
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '同一实体记录的事务级版本号计数器。',
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID。',
  `last_version_no` int NOT NULL DEFAULT '0' COMMENT '最近版本编号。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`entity_code`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录版本计数器表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_record_version_dataset` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：数据集ID。',
  `version_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务版本ID。',
  `node_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定范围节点编码。',
  `node_kind` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RELATION' COMMENT '节点种类。',
  `relation_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结关系编码。',
  `relation_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结关系中文名称。',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子实体编码。',
  `entity_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结子实体中文名称。',
  `entity_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体发布ID。',
  `entity_release_version` int DEFAULT NULL COMMENT '实体发布版本。',
  `selector_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结关系、过滤和排序选择器JSON。',
  `presentation_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结中文表单展示定义JSON。',
  `data_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据哈希。',
  `presentation_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示哈希。',
  `scope_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '范围哈希。',
  `row_count` int NOT NULL DEFAULT '0' COMMENT '行数量。',
  `complete` tinyint NOT NULL DEFAULT '1' COMMENT 'V2必须完整，禁止静默截断。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_record_version_dataset_node` (`version_id`,`node_code`),
  KEY `idx_entity_record_version_dataset_relation` (`relation_code`,`entity_code`),
  CONSTRAINT `fk_entity_record_version_dataset_version` FOREIGN KEY (`version_id`) REFERENCES `entity_record_version` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录版本关系数据集表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_record_version_dataset_row` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：数据集行ID。',
  `dataset_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集ID。',
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子记录稳定ID。',
  `record_title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '冻结业务中文标题。',
  `row_order` int NOT NULL DEFAULT '0' COMMENT '冻结顺序。',
  `row_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行哈希。',
  `values_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '值集合文档：fieldCode到FrozenValue的JSON。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_record_version_dataset_row` (`dataset_id`,`record_id`),
  KEY `idx_entity_record_version_dataset_order` (`dataset_id`,`row_order`,`record_id`),
  CONSTRAINT `fk_entity_record_version_dataset_row_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `entity_record_version_dataset` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录版本数据集行表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_relation` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `parent_entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主实体ID。',
  `parent_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主实体编码。',
  `parent_field_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主实体关系字段ID；关系已与 SUB_FORM 字段生命周期解耦，保留旧父字段关联用于回退或兼容。',
  `parent_field_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧版承载关系的父字段编码，仅兼容使用；关系已与 SUB_FORM 字段生命周期解耦，保留旧父字段关联用于回退或兼容。',
  `relation_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关系编码。',
  `relation_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关系名称。',
  `data_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '聚合数据中的稳定属性名。',
  `child_entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子实体ID。',
  `child_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子实体编码。',
  `child_ref_field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子实体回填主数据ID字段。',
  `relation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT '关系类型：ONE_TO_ONE/ONE_TO_MANY。',
  `ownership_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPOSITION' COMMENT '关系所有权：COMPOSITION/ASSOCIATION。',
  `cascade_delete` tinyint DEFAULT '1' COMMENT '主数据删除时是否级联删除子数据。',
  `required` tinyint DEFAULT '0' COMMENT '是否必需：是否必填。',
  `sort_order` int DEFAULT '0' COMMENT '排序号：排序。',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_relation_code` (`parent_entity_id`,`relation_code`),
  UNIQUE KEY `uk_parent_field` (`parent_entity_id`,`parent_field_code`),
  UNIQUE KEY `uk_entity_relation_data_key` (`parent_entity_id`,`data_key`),
  KEY `idx_parent_entity` (`parent_entity_id`,`enabled`,`deleted`),
  KEY `idx_parent_code` (`parent_entity_code`,`enabled`,`deleted`),
  KEY `idx_child_entity` (`child_entity_id`),
  KEY `idx_relation_code` (`relation_code`),
  KEY `idx_entity_relation_child_ref` (`child_entity_id`,`child_ref_field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体关系定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_schema_operation` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：操作ID。',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态。',
  `plan_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可变DDL计划摘要。',
  `idempotency_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键。',
  `plan_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可变DDL计划JSON。',
  `target_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标结构指纹。',
  `actual_fingerprint` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际结构指纹。',
  `drift_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '结构漂移JSON。',
  `unique_conflict_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '唯一性冲突扫描结果JSON。',
  `risk_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOW' COMMENT '风险级别。',
  `risk_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险原因。',
  `estimated_rows` bigint NOT NULL DEFAULT '0' COMMENT '预估数据行数。',
  `lock_risk` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOW' COMMENT '锁表风险。',
  `release_window` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '建议发布窗口。',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '执行次数。',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '最近失败信息。',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间。',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `operation_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENTITY_PUBLISH' COMMENT '操作来源；INDEX_ADVISOR 来源仅保留历史记录；实体发布来源仍在使用。',
  `source_reference_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '索引建议等来源记录ID；INDEX_ADVISOR 来源仅保留历史记录；实体发布来源仍在使用。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_schema_operation_plan` (`entity_id`,`plan_hash`),
  UNIQUE KEY `uk_entity_schema_operation_idempotency` (`idempotency_key`),
  KEY `idx_entity_schema_operation_latest` (`entity_id`,`create_time`),
  KEY `idx_entity_schema_operation_status` (`status`,`update_time`),
  KEY `idx_schema_operation_source` (`operation_source`,`source_reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体结构发布操作表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_schema_operation_event` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：事件ID。',
  `operation_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作ID。',
  `from_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原状态。',
  `to_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标状态。',
  `message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态说明。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  KEY `idx_entity_schema_operation_event` (`operation_id`,`create_time`),
  CONSTRAINT `fk_entity_schema_operation_event_operation` FOREIGN KEY (`operation_id`) REFERENCES `entity_schema_operation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体结构操作事件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_status` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `status_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态编码（系统标识）。',
  `status_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态名称（显示用）。',
  `status_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：状态说明。',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态颜色（如：#67C23A）。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_status` (`entity_code`,`status_code`,`deleted`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_status_category` (`status_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体状态定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_unique_value` (
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码。',
  `value_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规范化值摘要。',
  `normalized_value` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规范化值审计副本。',
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '占用该值的记录ID。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`entity_code`,`field_code`,`value_hash`),
  KEY `idx_entity_unique_value_record` (`entity_code`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体字段唯一值预留表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version_config` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：配置ID。',
  `entity_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体定义ID。',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用数据版本。',
  `contract_version` int NOT NULL DEFAULT '1' COMMENT '旧配置契约版本：供兼容桥和尚未升级的旧 Pod 使用。',
  `draft_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '旧草稿JSON：供兼容路由、双写桥和尚未升级的旧 Pod 使用；新核心运行时不读取。',
  `config_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '当前生效配置JSON：V2 触发器、范围和比较策略；保存后立即参与后续版本捕获。过渡期可空，以兼容旧 Pod 创建未发布配置。',
  `migration_state` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW_REQUIRED' COMMENT '旧迁移状态：供兼容桥和旧 Pod 保持旧契约。',
  `active_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧运行发布ID：由兼容桥维护，供尚未升级的旧 Pod 定位发布快照。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：当前配置修订号，用于 If-Match 乐观锁；不代表可回退的配置版本。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '旧发布状态：供兼容路由和旧 Pod 区分草稿、已发布。',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version_config_code` (`entity_code`,`deleted`),
  KEY `idx_entity_version_config_release` (`active_release_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体数据版本策略表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version_config_release` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：旧发布快照ID。',
  `config_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本配置ID。',
  `version` int NOT NULL COMMENT '版本号：旧发布版本号。',
  `contract_version` int NOT NULL DEFAULT '1' COMMENT '配置契约版本：旧配置契约版本。',
  `config_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '旧不可变配置JSON：由兼容桥写入，供尚未升级的旧 Pod 读取。',
  `scope_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布时范围摘要：旧发布时冻结范围摘要。',
  `published_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人：旧发布人。',
  `published_by_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人名称：旧发布人名称。',
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间：旧发布时间。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version_config_release` (`config_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体版本策略发布兼容表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_CHANNEL_DEFINITION` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `VERSION_` int DEFAULT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `CREATE_TIME_` datetime(3) DEFAULT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `RESOURCE_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源文件名，定位部署包内的模型内容',
  `DESCRIPTION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  `TYPE_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `IMPLEMENTATION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IMPLEMENTATION_ 引擎属性，供 Flowable 事件注册或批处理事件通道定义表，保存引擎定义与运行状态在模型管理或实例执行时使用',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_CHANNEL_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件注册或批处理事件通道定义表，保存引擎定义与运行状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_EVENT_DEFINITION` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `VERSION_` int DEFAULT NULL COMMENT '定义版本，供引擎选择和历史追踪',
  `KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务键，用于查找对应的定义或实例',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `RESOURCE_NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源文件名，定位部署包内的模型内容',
  `DESCRIPTION_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明文本，供定义管理和历史查询展示',
  PRIMARY KEY (`ID_`),
  UNIQUE KEY `ACT_IDX_EVENT_DEF_UNIQ` (`KEY_`,`VERSION_`,`TENANT_ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件注册或批处理事件定义表，保存引擎定义与运行状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_EVENT_DEPLOYMENT` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `CATEGORY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标识，供模型和定义按业务类型检索',
  `DEPLOY_TIME_` datetime(3) DEFAULT NULL COMMENT '部署时间，用于模型版本管理和历史追踪',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户标识，用于隔离不同租户的引擎记录',
  `PARENT_DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件注册或批处理事件部署包表，保存引擎定义与运行状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_EVENT_RESOURCE` (
  `ID_` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `NAME_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称，供引擎管理界面展示',
  `DEPLOYMENT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署包ID，关联模型及其资源',
  `RESOURCE_BYTES_` longblob COMMENT '资源二进制内容，保存部署时上传的模型文件',
  PRIMARY KEY (`ID_`),
  KEY `FLW_IDX_EVENT_RSRC_DPL` (`DEPLOYMENT_ID_`),
  CONSTRAINT `FLW_FK_EVENT_RSRC_DPL` FOREIGN KEY (`DEPLOYMENT_ID_`) REFERENCES `FLW_EVENT_DEPLOYMENT` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件注册或批处理事件资源表，保存引擎定义与运行状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_RU_BATCH` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `SEARCH_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SEARCH_KEY_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态在模型管理或实例执行时使用',
  `SEARCH_KEY2_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SEARCH_KEY2_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态在模型管理或实例执行时使用',
  `CREATE_TIME_` datetime(3) NOT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `COMPLETE_TIME_` datetime(3) DEFAULT NULL COMMENT '完成时间，用于批处理结果和耗时统计',
  `STATUS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批处理状态，决定后续调度或结果查询',
  `BATCH_DOC_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BATCH_DOC 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FLW_RU_BATCH_PART` (
  `ID_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引擎记录主键，供内部表关联和状态更新',
  `REV_` int DEFAULT NULL COMMENT '乐观锁版本，防止并发覆盖引擎状态',
  `BATCH_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BATCH 标识，关联 Flowable 引擎中的对应记录',
  `TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型，决定引擎采用的处理方式',
  `SCOPE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SUB_SCOPE_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录',
  `SCOPE_TYPE_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域类型，区分流程、案例等引擎上下文',
  `SEARCH_KEY_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SEARCH_KEY_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态在模型管理或实例执行时使用',
  `SEARCH_KEY2_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SEARCH_KEY2_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态在模型管理或实例执行时使用',
  `CREATE_TIME_` datetime(3) NOT NULL COMMENT '创建时间，用于引擎记录排序和追踪',
  `COMPLETE_TIME_` datetime(3) DEFAULT NULL COMMENT '完成时间，用于批处理结果和耗时统计',
  `STATUS_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批处理状态，决定后续调度或结果查询',
  `RESULT_DOC_ID_` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'RESULT_DOC 标识，关联 Flowable 引擎中的对应记录',
  `TENANT_ID_` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '租户标识，用于隔离不同租户的引擎记录',
  PRIMARY KEY (`ID_`),
  KEY `FLW_IDX_BATCH_PART` (`BATCH_ID_`),
  CONSTRAINT `FLW_FK_BATCH_PART_PARENT` FOREIGN KEY (`BATCH_ID_`) REFERENCES `FLW_RU_BATCH` (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_api_request_lease` (
  `lease_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租约ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `scope_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '并发配额范围键：区分请求类别的内部键，不表示 OAuth Scope。',
  `expires_at` datetime(6) NOT NULL COMMENT '过期时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`lease_id`),
  KEY `idx_integration_api_lease_application` (`application_id`,`expires_at`),
  KEY `idx_integration_api_lease_expiry` (`expires_at`),
  KEY `idx_integration_api_lease_scope` (`application_id`,`scope_key`,`expires_at`),
  CONSTRAINT `fk_integration_api_lease_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Embed 请求并发租约表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_application` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `client_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端ID。',
  `application_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用名称。',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `owner_organization_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所有者组织ID。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；CHECK 枚举：\'ACTIVE\',\'DISABLED\',\'REVOKED\'。',
  `rate_limit_per_minute` int NOT NULL DEFAULT '60' COMMENT '每分钟请求上限。',
  `max_concurrency` int NOT NULL DEFAULT '10' COMMENT '最大并发数。',
  `allowed_source_cidrs` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '允许的来源网段集合。',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '过期时间。',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '版本号。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修改人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_application_client_id` (`client_id`),
  KEY `idx_integration_application_owner` (`owner_organization_id`,`status`),
  KEY `idx_integration_application_status_expiry` (`status`,`expires_at`),
  CONSTRAINT `chk_integration_application_cidrs` CHECK (((`allowed_source_cidrs` is null) or json_valid(`allowed_source_cidrs`))),
  CONSTRAINT `chk_integration_application_concurrency` CHECK ((`max_concurrency` between 1 and 1000)),
  CONSTRAINT `chk_integration_application_rate_limit` CHECK ((`rate_limit_per_minute` between 1 and 10000)),
  CONSTRAINT `chk_integration_application_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部集成应用表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_application_credential` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `secret_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密钥哈希：应用凭证哈希，用于验证凭证，不能反推出原始凭证。',
  `credential_hint` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '凭证提示。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态；CHECK 枚举：\'ACTIVE\',\'REVOKED\'。',
  `credential_version` bigint NOT NULL COMMENT '凭证版本。',
  `expires_at` datetime(6) DEFAULT NULL COMMENT '过期时间。',
  `last_used_at` datetime(6) DEFAULT NULL COMMENT '最近使用时间。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销人。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `active_application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then `application_id` else NULL end)) STORED COMMENT '活跃应用ID；数据库生成，表达式见本表实现说明。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_credential_version` (`application_id`,`credential_version`),
  UNIQUE KEY `uk_integration_credential_active` (`active_application_id`),
  KEY `idx_integration_credential_application` (`application_id`,`status`,`create_time`),
  CONSTRAINT `fk_integration_credential_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_credential_revocation` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`revoked_at` is null) and (`revoked_by` is null)) or ((`status` = _utf8mb4'REVOKED') and (`revoked_at` is not null) and (`revoked_by` is not null)))),
  CONSTRAINT `chk_integration_credential_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成应用凭证表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_idempotency_record` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用ID。',
  `operation` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作。',
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。',
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态；CHECK 枚举： \'PROCESSING\',\'SUCCEEDED\',\'FAILED_RETRYABLE\' 。',
  `resource_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源类型。',
  `resource_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源ID。',
  `response_status` smallint DEFAULT NULL COMMENT '响应状态。',
  `response_body` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '响应正文。',
  `fencing_token` bigint NOT NULL DEFAULT '1' COMMENT '执行隔离代次。',
  `processing_started_at` datetime(6) NOT NULL COMMENT '处理开始时间。',
  `expires_at` datetime(6) NOT NULL COMMENT '过期时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_idempotency_operation` (`application_id`,`operation`,`idempotency_key`),
  KEY `idx_integration_idempotency_expiry` (`expires_at`,`status`),
  KEY `idx_integration_idempotency_resource` (`application_id`,`resource_type`,`resource_id`),
  CONSTRAINT `fk_integration_idempotency_application` FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_idempotency_fencing` CHECK ((`fencing_token` > 0)),
  CONSTRAINT `chk_integration_idempotency_hash` CHECK (regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_integration_idempotency_response` CHECK ((((`status` = _utf8mb4'SUCCEEDED') and (`resource_type` is not null) and (`resource_id` is not null) and (`response_status` between 200 and 299) and (`response_body` is not null) and json_valid(`response_body`)) or ((`status` <> _utf8mb4'SUCCEEDED') and (`response_status` is null) and (`response_body` is null)))),
  CONSTRAINT `chk_integration_idempotency_status` CHECK ((`status` in (_utf8mb4'PROCESSING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED_RETRYABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Embed 写入幂等记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_rate_limit_bucket` (
  `bucket_key` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '桶键。',
  `window_epoch` bigint NOT NULL COMMENT '窗口时间戳。',
  `request_count` int NOT NULL DEFAULT '0' COMMENT '请求数量。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`bucket_key`,`window_epoch`),
  KEY `idx_integration_rate_bucket_updated` (`update_time`),
  CONSTRAINT `chk_integration_rate_bucket_count` CHECK ((`request_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成应用限流桶表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：流程动作配置 用于流程、节点和顺序流上配置的接口动作。',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义配置ID。',
  `scope_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SEQUENCE_FLOW' COMMENT '作用域：PROCESS/NODE/SEQUENCE_FLOW。',
  `element_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BPMN元素ID，流程级为空。',
  `trigger_timing` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'TRANSITION_TAKEN' COMMENT '业务触发时机。',
  `execution_mode` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'IN_TRANSACTION' COMMENT '执行方式：IN_TRANSACTION/AFTER_COMMIT。',
  `failure_policy` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ROLLBACK' COMMENT '失败策略：ROLLBACK/CONTINUE/RETRY/IGNORE。',
  `retry_config` text COLLATE utf8mb4_unicode_ci COMMENT '重试配置JSON。',
  `action_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作定义目录ID。',
  `action_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作名称。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：动作描述。',
  `interface_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接口名称（Spring Bean或类名）。',
  `params_json` text COLLATE utf8mb4_unicode_ci COMMENT '参数JSON。',
  `sort_order` int DEFAULT '0' COMMENT '排序号：执行顺序。',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED。',
  `version_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属版本ID。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除标记：是否删除 0-未删除 1-已删除。',
  PRIMARY KEY (`id`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_version` (`version_id`),
  KEY `idx_status` (`status`),
  KEY `idx_process_action_binding` (`process_config_id`,`scope_type`,`element_id`,`trigger_timing`,`status`,`deleted`),
  KEY `idx_process_action_version_binding` (`version_id`,`scope_type`,`element_id`,`trigger_timing`,`status`,`deleted`),
  KEY `idx_process_action_definition_id` (`action_definition_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：主键。',
  `action_code` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定动作编码，默认使用Spring Bean名称。',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作中文名称。',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：动作用途说明。',
  `handler_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理器名称：FlowActionHandler Bean名称。',
  `visibility_scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENTITY' COMMENT '可见范围：GLOBAL/ENTITY。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：是否允许在流程设计器中选择。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除标识：0-未删除，1-已删除。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_definition_code` (`action_code`),
  UNIQUE KEY `uk_process_action_definition_handler` (`handler_name`),
  KEY `idx_process_action_definition_scope` (`visibility_scope`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作处理器目录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action_definition_entity` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：主键（UUID）。',
  `action_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作定义 ID。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '可见的实体编码。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_definition_entity` (`action_definition_id`,`entity_code`),
  KEY `idx_process_action_entity_code` (`entity_code`,`action_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作可见实体表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action_execution` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：执行记录主键。',
  `action_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作ID：关联的 process_action 动作配置 ID。',
  `action_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作名称快照。',
  `handler_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器Bean名称快照。',
  `handler_display_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器中文名称快照。',
  `version_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属流程发布版本 ID。',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例 ID。',
  `process_definition_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable 流程定义 ID。',
  `execution_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable 执行实例 ID。',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务 ID（任务级动作）。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体编码快照。',
  `scope_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域类型：PROCESS、NODE、SEQUENCE_FLOW。',
  `element_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '绑定的 BPMN 元素 ID。',
  `trigger_timing` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发时机编码。',
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键，防止同一动作重复执行。',
  `payload_json` text COLLATE utf8mb4_unicode_ci COMMENT '触发事件序列化 JSON（执行上下文载荷）。',
  `resolved_params_json` text COLLATE utf8mb4_unicode_ci COMMENT '表达式解析后的动作参数。',
  `result_json` text COLLATE utf8mb4_unicode_ci COMMENT '动作执行结果。',
  `execution_trace_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '结构化执行步骤轨迹。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：执行状态，对应 Status。',
  `owner_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '持有者ID：当前执行租约所有者。',
  `lease_token` bigint NOT NULL DEFAULT '0' COMMENT '租约代次：单调递增 fencing token。',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约截止时间：当前执行租约到期时间（数据库 UTC）。',
  `retry_count` int DEFAULT '0' COMMENT '已重试次数。',
  `max_retries` int DEFAULT '5' COMMENT '最大重试次数。',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间。',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息（截断）。',
  `error_stack` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '异常堆栈。',
  `started_at` datetime DEFAULT NULL COMMENT '开始执行时间。',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间。',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时毫秒。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_execution_idempotency` (`idempotency_key`),
  KEY `idx_process_action_execution_ready` (`status`,`next_retry_time`,`create_time`),
  KEY `idx_process_action_execution_process` (`process_instance_id`,`create_time`),
  KEY `idx_process_action_execution_action` (`action_id`,`create_time`),
  KEY `idx_process_action_execution_entity` (`entity_code`,`process_instance_id`,`create_time`),
  KEY `idx_process_action_execution_lease` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作执行与重试表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_assignee_incident` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程配置ID。',
  `process_definition_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID。',
  `process_instance_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID。',
  `task_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID。',
  `node_id` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID。',
  `node_name` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称。',
  `policy` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略。',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态。',
  `empty_reason_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '为空原因编码。',
  `empty_reason_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '为空原因消息。',
  `resolver_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解析器编码。',
  `resolver_extra_params_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '解析器额外参数JSON。',
  `fallback_user` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '后备用户。',
  `fallback_group` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '后备用户组。',
  `responsibility_owner` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '责任所有者。',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试数量。',
  `max_retries` int NOT NULL DEFAULT '0' COMMENT '最大重试次数。',
  `initial_delay_seconds` int DEFAULT NULL COMMENT '初始延迟秒数。',
  `backoff_multiplier` decimal(8,3) DEFAULT NULL COMMENT '退避倍数。',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下一次重试时间。',
  `resolution_action` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处置动作。',
  `resolved_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解析完成人员。',
  `resolved_at` datetime DEFAULT NULL COMMENT '解析完成时间。',
  `detail_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '详情JSON。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `open_slot` varchar(128) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'OPEN',_utf8mb4'RETRY_SCHEDULED',_utf8mb4'MANUAL_REQUIRED')) then concat(coalesce(`task_id`,`process_instance_id`,_utf8mb4'NO_INSTANCE'),_utf8mb4':',`node_id`) else NULL end)) STORED COMMENT '开放占位；数据库生成，表达式见本表实现说明。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_assignee_incident_open` (`open_slot`),
  KEY `idx_process_assignee_incident_status` (`status`,`next_retry_at`),
  KEY `idx_process_assignee_incident_instance` (`process_instance_id`,`create_time`),
  KEY `idx_process_assignee_incident_owner` (`responsibility_owner`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空办理人阻断事件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_assignee_incident_action` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `incident_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件ID。',
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等请求ID。',
  `action_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作类型。',
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态。',
  `operator` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作人。',
  `request_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '请求JSON。',
  `result_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '结果JSON。',
  `error_message` varchar(1500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误消息。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assignee_incident_action_request` (`incident_id`,`request_id`),
  KEY `idx_assignee_incident_action` (`incident_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空办理人处置审计表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_cc_record` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID。',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID。',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程Key。',
  `process_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程名称。',
  `data_name` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程数据名称快照：创建知会时保存业务数据当时的名称，供知会列表和通知展示。后续业务数据改名不更新此值；历史记录保持为空，不回查当前业务表补写。',
  `business_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务Key。',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点ID。',
  `node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称。',
  `cc_user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抄送人ID。',
  `cc_user_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抄送人名称。',
  `cc_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'AUTO' COMMENT '抄送类型：AUTO自动/MANUAL手动。',
  `cc_timing` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抄送时机。',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID。',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人名称。',
  `comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知会备注。',
  `source_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源任务ID。',
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型。',
  `recipient_rule_snapshot` text COLLATE utf8mb4_unicode_ci COMMENT '收件人规则快照。',
  `unique_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '幂等键。',
  `read_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD未读/READ已读。',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_cc_unique_key` (`unique_key`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_cc_user` (`cc_user_id`,`read_status`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_process_cc_source_task` (`source_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程抄送记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_definition_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供流程定义配置表中的记录关联；由数据库分配',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识。',
  `process_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程名称。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：流程描述。',
  `category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程分类。',
  `version` int DEFAULT '1' COMMENT '版本号。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用。',
  `bpmn_xml` text COLLATE utf8mb4_unicode_ci COMMENT 'BPMN XML内容。',
  `draft_revision` bigint NOT NULL DEFAULT '1' COMMENT '草稿修订号：流程草稿修订号。',
  `published_revision` bigint NOT NULL DEFAULT '0' COMMENT '发布修订号：最近发布对应的草稿修订号。',
  `draft_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前流程草稿 SHA-256。',
  `published_draft_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近发布流程草稿 SHA-256。',
  `base_published_version` int NOT NULL DEFAULT '0' COMMENT '当前草稿基于的已发布版本。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除标记：是否删除 0-未删除 1-已删除。',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '绑定实体ID。',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人：更新人。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `process_key` (`process_key`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=2099424243996549122 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_entity_status_mapping` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义配置ID。',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `sequence_flow_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '连线ID（BPMN中的sequenceFlowId）。',
  `source_node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源节点ID。',
  `source_node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源节点名称。',
  `target_node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标节点ID。',
  `target_node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标节点名称。',
  `entity_status_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体状态编码（关联entity_status表）。',
  `condition_expression` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条件表达式。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明描述。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `entity_status` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体数据状态值（如:审批中、已通过、已驳回）。',
  `status_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_source_target` (`process_config_id`,`source_node_id`,`target_node_id`,`deleted`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_source_node` (`source_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体流程状态映射表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_form_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供流程节点表单配置表中的记录关联；由数据库分配',
  `node_config_id` bigint NOT NULL COMMENT '所属节点配置ID。',
  `form_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单名称。',
  `form_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单标识。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：表单描述。',
  `is_readonly` tinyint(1) DEFAULT '0' COMMENT '是否只读。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `entity_form_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体表单ID。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  PRIMARY KEY (`id`),
  KEY `idx_node_config_id` (`node_config_id`),
  KEY `idx_form_key` (`form_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2082646087759310850 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点表单配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_form_field_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供流程节点表单字段表中的记录关联；由数据库分配',
  `form_config_id` bigint NOT NULL COMMENT '所属表单配置ID。',
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称。',
  `field_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段标识。',
  `field_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段类型。',
  `is_required` tinyint(1) DEFAULT '0' COMMENT '是否必填。',
  `default_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认值。',
  `options_json` text COLLATE utf8mb4_unicode_ci COMMENT '选项配置JSON。',
  `validate_rules` text COLLATE utf8mb4_unicode_ci COMMENT '验证规则JSON。',
  `sort_order` int DEFAULT '0' COMMENT '排序号：排序顺序。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  PRIMARY KEY (`id`),
  KEY `idx_form_config_id` (`form_config_id`),
  KEY `idx_field_key` (`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点表单字段表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_approval` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：流程节点审批配置。',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程配置ID。',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID（bpmn元素ID）。',
  `node_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称。',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用审批意见：0-否 1-是。',
  `comment_label` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '审批意见' COMMENT '审批意见标签。',
  `options_json` text COLLATE utf8mb4_unicode_ci COMMENT '选项配置JSON：审批选项JSON。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  KEY `idx_process_node` (`process_config_id`,`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点审批配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_approval_option` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `approval_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批配置ID：关联的审批配置ID。',
  `option_value` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项值（如 approve/reject/return）。',
  `option_label` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项显示名称。',
  `style_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '按钮样式类型（如 primary/success/danger）。',
  `show_comment` tinyint NOT NULL DEFAULT '1' COMMENT '是否显示意见输入：是否显示审批意见输入框。',
  `remark_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否要求备注：是否强制要求填写备注。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `option_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '审批项扩展JSON文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_approval_option` (`approval_config_id`,`option_value`),
  KEY `idx_process_approval_option_sort` (`approval_config_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点审批选项表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_assignee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供流程节点办理人配置表中的记录关联；由数据库分配',
  `node_config_id` bigint NOT NULL COMMENT '所属节点配置ID。',
  `assignee_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批人类型。',
  `assignee_value` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批人值。',
  `assignee_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人显示名称。',
  `priority` int DEFAULT '0' COMMENT '优先级。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  PRIMARY KEY (`id`),
  KEY `idx_node_config_id` (`node_config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2102556042004025346 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点办理人配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供流程节点配置表中的记录关联；由数据库分配',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID。',
  `node_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点名称。',
  `node_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型。',
  `process_config_id` bigint NOT NULL COMMENT '所属流程配置ID。',
  `config_json` text COLLATE utf8mb4_unicode_ci COMMENT '扩展配置JSON。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `skip_node` tinyint DEFAULT '0' COMMENT '是否跳过节点。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：是否删除。',
  PRIMARY KEY (`id`),
  KEY `idx_process_config_id` (`process_config_id`),
  KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2102556042024996866 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_form` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程配置ID。',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID（bpmn元素ID）。',
  `node_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '节点名称。',
  `form_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单ID。',
  `is_readonly` tinyint DEFAULT '0' COMMENT '是否只读：0-否 1-是。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_config_id`,`node_id`),
  KEY `idx_process_config_id` (`process_config_id`),
  KEY `idx_form_id` (`form_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点实体表单绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_operation_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID。',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联的任务ID。',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC。',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID。',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名。',
  `operation_time` datetime DEFAULT NULL COMMENT '操作时间。',
  `operation_comment` text COLLATE utf8mb4_unicode_ci COMMENT '操作备注/审批意见。',
  `old_value` text COLLATE utf8mb4_unicode_ci COMMENT '旧值（JSON）。',
  `new_value` text COLLATE utf8mb4_unicode_ci COMMENT '新值（JSON）。',
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作来源IP地址。',
  `user_agent` text COLLATE utf8mb4_unicode_ci COMMENT 'User-Agent：操作来源客户端User-Agent。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `old_value_format` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'JSON' COMMENT '原值格式。',
  `new_value_format` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'JSON' COMMENT '新值格式。',
  PRIMARY KEY (`id`),
  KEY `idx_process` (`process_instance_id`,`operation_time`),
  KEY `idx_operator` (`operator_id`,`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_person_resolver_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：人员解析器定义ID。',
  `resolver_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定解析器编码。',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '中文名称。',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：用途说明。',
  `bean_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Spring Bean名称。',
  `implementation_version` int NOT NULL DEFAULT '1' COMMENT '实现版本。',
  `contract_version` int NOT NULL DEFAULT '1' COMMENT '平台契约版本。',
  `supported_usages_document` text COLLATE utf8mb4_unicode_ci COMMENT '支持的用途集合文档。',
  `extra_param_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '额外参数结构文档。',
  `dynamic_extra_params` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许动态extraParams。',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用：是否允许在流程配置中选择。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：目录修订号。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_person_resolver_code` (`resolver_code`,`deleted`),
  KEY `idx_person_resolver_enabled` (`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受控人员解析器目录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_status_sync_event` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID。',
  `event_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型。',
  `event_sequence` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件序列。',
  `entity_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码。',
  `entity_record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体记录ID。',
  `target_status` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标状态。',
  `status_category` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类。',
  `state` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理状态。',
  `applied_at` datetime(6) DEFAULT NULL COMMENT '应用时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_status_sync_event` (`process_instance_id`,`event_type`,`event_sequence`),
  KEY `idx_process_status_sync_entity` (`entity_code`,`entity_record_id`,`create_time`),
  KEY `idx_process_status_sync_state` (`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实体状态同步事件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供平台流程任务表中的记录关联；由数据库分配',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID。',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义ID。',
  `process_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识。',
  `process_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程名称。',
  `node_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID。',
  `node_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称。',
  `node_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点类型。',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable任务ID。',
  `business_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务主键。',
  `entity_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体编码。',
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体数据ID。',
  `assignee_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行人ID。',
  `assignee_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行人姓名。',
  `assignee_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行人类型: user/group/role。',
  `form_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '表单标识。',
  `form_data` longtext COLLATE utf8mb4_unicode_ci COMMENT '表单数据。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'todo' COMMENT '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回。',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作: approve/reject/transfer/skip。',
  `action_label` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作显示文本，如"同意，需要会签"。',
  `comment` text COLLATE utf8mb4_unicode_ci COMMENT '审批意见。',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务开始时间。',
  `end_time` datetime DEFAULT NULL COMMENT '任务结束时间。',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间。',
  `sla_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SLA综合状态。',
  `response_due_time` datetime(6) DEFAULT NULL COMMENT '响应截止时间：首次响应截止时间。',
  `duration` bigint DEFAULT NULL COMMENT '处理耗时(毫秒)。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：删除标记: 0-正常 1-删除。',
  `timeout_hours` int DEFAULT NULL COMMENT '超时时间。',
  `timeout_action` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '超时策略。',
  `timeout_handled` tinyint DEFAULT '0' COMMENT '是否已处理超时。',
  `priority` int DEFAULT '0' COMMENT '优先级。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_id` (`task_id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_assignee` (`assignee_id`,`status`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_process_task_sla_status` (`sla_status`,`due_time`)
) ENGINE=InnoDB AUTO_INCREMENT=643 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台流程任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_add_sign` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID。',
  `source_task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发加签的源任务ID。',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '加签所在节点ID。',
  `operation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PARALLEL' COMMENT '加签类型（如 before前加签/after后加签/parallel并行加签）。',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作人ID。',
  `comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '加签操作备注。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：加签状态：PENDING-进行中，COMPLETED-已完成，CANCELED-已取消。',
  `engine_execution_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable引擎执行实例ID。',
  `source_completed` tinyint NOT NULL DEFAULT '0' COMMENT '原任务是否已提交。',
  `source_action` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原任务提交动作。',
  `source_action_label` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原任务动作名称。',
  `source_comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原任务审批意见。',
  `source_form_data` longtext COLLATE utf8mb4_unicode_ci COMMENT '原任务表单数据JSON。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `complete_time` datetime DEFAULT NULL COMMENT '加签完成时间。',
  PRIMARY KEY (`id`),
  KEY `idx_add_sign_source_task` (`source_task_id`,`status`),
  KEY `idx_add_sign_process` (`process_instance_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行时加签记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_add_sign_user` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `add_sign_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加签记录ID：关联的加签操作ID。',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被加签的用户ID。',
  `user_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名称快照：加签时的用户姓名快照。',
  `generated_task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生成的任务ID：加签生成的Flowable任务ID。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TODO' COMMENT '状态：用户任务状态：PENDING-待处理，COMPLETED-已完成。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号（控制串行加签顺序）。',
  `complete_time` datetime DEFAULT NULL COMMENT '用户处理完成时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_add_sign_user` (`add_sign_id`,`user_id`),
  UNIQUE KEY `uk_add_sign_generated_task` (`generated_task_id`),
  KEY `idx_add_sign_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行时加签人员表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_candidate_group` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `task_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联的流程任务实例ID。',
  `group_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '候选组编码（角色/部门等）。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号，控制候选组处理顺序。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_candidate_group` (`task_instance_id`,`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务候选组表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_candidate_user` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `task_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联的流程任务实例ID。',
  `user_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '候选用户ID。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号，控制候选用户处理顺序。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_candidate_user` (`task_instance_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务候选用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_sla` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID。',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID。',
  `process_definition_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID。',
  `process_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程键。',
  `node_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID。',
  `node_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称。',
  `business_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务键。',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体编码。',
  `entity_data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体数据ID。',
  `policy_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略编码。',
  `policy_version` int NOT NULL COMMENT '策略版本。',
  `policy_snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略快照JSON。',
  `calendar_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '日历编码。',
  `calendar_version` int DEFAULT NULL COMMENT '日历版本。',
  `calendar_snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '日历快照JSON。',
  `timezone_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '时区ID。',
  `current_assignee_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前办理人ID。',
  `started_at` datetime(6) NOT NULL COMMENT '开始时间。',
  `responded_at` datetime(6) DEFAULT NULL COMMENT '已响应时间。',
  `completed_at` datetime(6) DEFAULT NULL COMMENT '完成时间。',
  `response_due_at` datetime(6) DEFAULT NULL COMMENT '响应截止时间。',
  `completion_due_at` datetime(6) NOT NULL COMMENT '完成截止时间。',
  `response_remaining_minutes` int DEFAULT NULL COMMENT '剩余响应分钟数。',
  `completion_remaining_minutes` int DEFAULT NULL COMMENT '剩余完成分钟数。',
  `response_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '响应状态。',
  `completion_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '完成状态。',
  `overall_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING' COMMENT '整体状态。',
  `pause_started_at` datetime(6) DEFAULT NULL COMMENT '暂停开始时间。',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_sla_task` (`task_id`),
  KEY `idx_process_task_sla_process` (`process_instance_id`,`node_id`),
  KEY `idx_process_task_sla_response` (`response_status`,`response_due_at`),
  KEY `idx_process_task_sla_completion` (`completion_status`,`completion_due_at`),
  KEY `idx_process_task_sla_assignee` (`current_assignee_id`,`overall_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务时效运行台账表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_sla_event` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `sla_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '时效ID。',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID。',
  `step_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '步骤ID。',
  `event_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型。',
  `metric_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指标类型。',
  `trigger_at` datetime(6) NOT NULL COMMENT '触发时间。',
  `action_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作类型。',
  `action_config_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '动作配置快照。',
  `execution_no` int NOT NULL DEFAULT '1' COMMENT '执行编号。',
  `max_executions` int NOT NULL DEFAULT '1' COMMENT '最大执行次数。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态。',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '尝试次数。',
  `max_retries` int NOT NULL DEFAULT '5' COMMENT '最大重试次数。',
  `next_retry_time` datetime(6) DEFAULT NULL COMMENT '下一次重试时间。',
  `owner_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。',
  `lease_token` bigint NOT NULL DEFAULT '0' COMMENT '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。',
  `idempotency_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。',
  `result_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '结果JSON。',
  `error_message` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误消息。',
  `started_at` datetime(6) DEFAULT NULL COMMENT '开始时间。',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '完成时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_sla_event_key` (`idempotency_key`),
  KEY `idx_process_task_sla_event_ready` (`status`,`trigger_at`,`next_retry_time`),
  KEY `idx_process_task_sla_event_lease` (`status`,`lease_until`),
  KEY `idx_process_task_sla_event_sla` (`sla_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时效到期执行事件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_sla_pause` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `sla_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '时效ID。',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID。',
  `pause_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '暂停类型。',
  `reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原因。',
  `operator_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID。',
  `started_at` datetime(6) NOT NULL COMMENT '开始时间。',
  `resumed_at` datetime(6) DEFAULT NULL COMMENT '恢复时间。',
  `duration_seconds` bigint DEFAULT NULL COMMENT '耗时秒数。',
  `response_remaining_minutes` int DEFAULT NULL COMMENT '剩余响应分钟数。',
  `completion_remaining_minutes` int DEFAULT NULL COMMENT '剩余完成分钟数。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  KEY `idx_process_task_sla_pause` (`sla_id`,`started_at`,`resumed_at`),
  KEY `idx_process_task_sla_pause_task` (`task_id`,`resumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务时效暂停历史表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_ui_release_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：绑定记录ID。',
  `process_version_history_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程发布历史ID。',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程配置ID。',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识。',
  `process_version` int NOT NULL COMMENT '流程版本号。',
  `deployment_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable部署ID。',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程节点ID。',
  `node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程节点名称。',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FORM' COMMENT '配置类型。',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单或列表配置ID。',
  `pinned_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程发布时固定的UI发布ID。',
  `pinned_release_version` int NOT NULL COMMENT '流程发布时固定的UI版本号。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_ui_release_binding` (`process_version_history_id`,`node_id`,`config_type`,`config_id`),
  KEY `idx_process_ui_binding_config` (`config_type`,`config_id`,`process_version_history_id`),
  KEY `idx_process_ui_binding_release` (`pinned_release_id`,`process_version_history_id`),
  KEY `idx_process_ui_binding_deployment` (`deployment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程与界面发布绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_version_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，供流程发布历史表中的记录关联；由数据库分配',
  `process_config_id` bigint NOT NULL COMMENT '流程定义ID。',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识。',
  `process_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程名称。',
  `version` int NOT NULL COMMENT '版本号。',
  `version_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本描述/发布说明。',
  `bpmn_xml` text COLLATE utf8mb4_unicode_ci COMMENT 'BPMN XML内容。',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间。',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人ID。',
  `deployment_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable部署ID。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-有效，ARCHIVED-已归档。',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除标记：是否删除 0-未删除 1-已删除。',
  `node_forms_snapshot` longtext COLLATE utf8mb4_unicode_ci COMMENT '节点表单绑定快照JSON。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_version` (`process_config_id`,`version`),
  KEY `idx_process_config_id` (`process_config_id`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_version` (`version`),
  KEY `idx_deployment_id` (`deployment_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2102556041735589891 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程发布历史表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storage_file_object` (
  `id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `storage_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储URL。',
  `storage_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储键。',
  `owner_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所有者用户ID。',
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。',
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。',
  `original_name` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始名称。',
  `content_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容类型。',
  `content_length` bigint NOT NULL DEFAULT '0' COMMENT '内容长度。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_storage_file_url` (`storage_url`(768)),
  UNIQUE KEY `uk_storage_file_owner_idempotency` (`owner_user_id`,`idempotency_key`),
  KEY `idx_storage_file_owner` (`owner_user_id`,`deleted`,`create_time`),
  CONSTRAINT `chk_storage_file_idempotency` CHECK ((((`idempotency_key` is null) and (`request_hash` is null)) or ((`idempotency_key` is not null) and regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件对象归属表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `dict_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典编码。',
  `dict_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：描述。',
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态：0-启用 1-禁用。',
  `sort` int DEFAULT '0' COMMENT '排序。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `dict_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属字典ID。',
  `dict_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冗余：字典编码（便于直接查询）。',
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '父项ID，0表示顶级。',
  `item_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项编码。',
  `item_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项标签/显示文本。',
  `item_value` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项值。',
  `sort` int DEFAULT '0' COMMENT '排序。',
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态：0-启用 1-禁用。',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  KEY `idx_dict_id` (`dict_id`),
  KEY `idx_dict_code` (`dict_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_dict_item_lookup` (`dict_code`,`item_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_external_system` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID：外部系统ID',
  `system_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统名称：外部系统名称',
  `system_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统编码：外部系统稳定编码，删除后也不得复用',
  `status` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `address` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统地址：外部系统地址',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：描述',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '版本号：乐观锁版本号',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动刷新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除：0-正常 1-删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_external_system_code` (`system_code`),
  KEY `idx_sys_external_system_status_name` (`deleted`,`status`,`system_name`),
  CONSTRAINT `chk_sys_external_system_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_external_system_status` CHECK ((`status` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `chk_sys_external_system_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部系统基础信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_external_system_parameter` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID：参数ID',
  `external_system_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '外部系统ID',
  `parameter_name_zh` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数中文名',
  `parameter_name_en` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数英文名',
  `parameter_value` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值：普通配置参数值，敏感凭据应使用受控密钥存储',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动刷新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除：0-正常 1-删除',
  `active_parameter_name_en` varchar(100) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`deleted` = 0) then `parameter_name_en` else NULL end)) STORED COMMENT '活动参数英文名：仅活动参数参与英文名唯一约束；生成表达式：``(case when (`deleted` = 0) then `parameter_name_en` else NULL end)``',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_external_system_parameter_active_name` (`external_system_id`,`active_parameter_name_en`),
  KEY `idx_sys_external_system_parameter_order` (`external_system_id`,`deleted`,`sort_order`,`id`),
  CONSTRAINT `fk_sys_external_system_parameter_system` FOREIGN KEY (`external_system_id`) REFERENCES `sys_external_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_sys_external_system_parameter_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_external_system_parameter_sort` CHECK ((`sort_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部系统扩展参数表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_global_setting` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置记录ID：由应用分配。修改、恢复默认时与 version 一起定位原记录；删除后重建使用新 ID，防止旧请求覆盖新设置。',
  `scope_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置作用域：SYSTEM 为系统值，USER 为个人覆盖值；服务端按注册规则限制某项设置允许使用的作用域。',
  `owner_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置归属：SYSTEM 固定为字符串 0；USER 保存 sys_user.id。个人接口从当前登录身份获取此值，不接受客户端指定其他用户。',
  `setting_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定设置键：程序按此键读取设置，例如 ui.layout.tabs_enabled。键须在 GlobalSettingRegistry 注册，单独向表中插入一个键不会自动增加功能。',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置名称：在管理界面展示该设置的用途。由注册定义提供，不能代替 setting_key 参与业务匹配。',
  `setting_value_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置值类型：BOOLEAN、NUMBER、STRING 或 JSON；后端据此校验值，前端据此选择输入控件。同一设置键的系统值和个人值必须使用注册类型。',
  `setting_value` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置值文本：应用按 JSON 格式序列化和解析，例如 true、20、双引号包裹的字符串或对象。不是数据库原生 JSON 列；拒绝顶层 null、类型不符及超过 16 KiB 的文本，合法的 false、0 和空字符串不能当成缺省。',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生效规则说明：展示取值含义、作用范围、继承关系和生效时机。说明本身不执行逻辑，实际行为由注册定义及设置使用方实现。',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本：更新、删除按 ID 和旧版本共同匹配，成功更新后递增；版本不符时拒绝覆盖，避免多个页面互相覆盖偏好。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人：创建设置记录的用户 ID，由服务端填写；迁移初始化等无操作用户的场景可为空。',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近修改人：最近一次修改设置的用户 ID，用于定位维护人员；历史操作仍由系统审计记录。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间：记录首次建立时间，修改设置值时保留。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间：更新时自动刷新，用于查看最后维护时间；并发控制使用 version。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_global_setting_owner_key` (`scope_type`,`owner_id`,`setting_key`),
  CONSTRAINT `chk_sys_global_setting_key` CHECK ((char_length(trim(`setting_key`)) > 0)),
  CONSTRAINT `chk_sys_global_setting_name` CHECK ((char_length(trim(`name`)) > 0)),
  CONSTRAINT `chk_sys_global_setting_scope_owner` CHECK ((((`scope_type` = _utf8mb4'SYSTEM') and (`owner_id` = _utf8mb4'0')) or ((`scope_type` = _utf8mb4'USER') and (char_length(trim(`owner_id`)) > 0) and (`owner_id` <> _utf8mb4'0')))),
  CONSTRAINT `chk_sys_global_setting_value_type` CHECK ((`setting_value_type` in (_utf8mb4'BOOLEAN',_utf8mb4'NUMBER',_utf8mb4'STRING',_utf8mb4'JSON'))),
  CONSTRAINT `chk_sys_global_setting_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局设置与个人偏好表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_group` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：组ID。',
  `group_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组名称。',
  `group_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组编码。',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '说明：描述。',
  `sort` int DEFAULT '0' COMMENT '排序。',
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0启用 1禁用）。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：删除标志。',
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父组ID。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户组表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：菜单ID。',
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '父菜单ID，0为顶级菜单。',
  `menu_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称。',
  `menu_type` char(1) COLLATE utf8mb4_unicode_ci DEFAULT 'M' COMMENT '菜单类型：M-目录 C-菜单 F-按钮。',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '菜单图标。',
  `sort` int DEFAULT '0' COMMENT '显示排序。',
  `path` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由地址。',
  `component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件路径。',
  `perm` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识，如：system:user:list。',
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '状态：0-禁用 1-启用。',
  `visible` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '显示状态：0-隐藏 1-显示。',
  `keep_alive` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '页面保活标记：是否缓存：0-不缓存 1-缓存。',
  `breadcrumb` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '面包屑标记：是否显示面包屑：0-否 1-是。',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注。',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除标记：是否删除：0-未删除 1-已删除。',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人：创建者。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人：更新者。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `is_frame` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '是否外链：0-否 1-是。',
  `is_cache` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '缓存标记：是否缓存：0-缓存 1-不缓存。',
  `query` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '路由参数。',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表。',
  `resource_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '菜单资源类型，ENTITY_LIST 表示动态实体列表。',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体列表稳定编码。',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_sort` (`sort`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单与功能权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_organization` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `org_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组织编码（唯一）。',
  `org_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组织名称。',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：org-组织，dept-部门。',
  `business_level_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '稳定业务层级编码，来源于 organization_business_level 字典。',
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '父级ID（顶级为0）。',
  `level` int DEFAULT '0' COMMENT '层级（0为顶级）。',
  `path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '/' COMMENT '完整路径，如：/0/1/5/10/。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  `leader_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人ID。',
  `leader_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人名称（冗余）。',
  `phone` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话。',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱。',
  `address` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址。',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态：0-启用，1-禁用。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：描述。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除标记：是否删除：0-未删除 1-已删除。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`org_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_type` (`type`),
  KEY `idx_path` (`path`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_sys_org_business_level` (`business_level_code`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：全局职务定义；职务本身不携带组织范围，也不产生系统权限。',
  `position_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职务编码。',
  `position_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职务名称。',
  `applicable_unit_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用单元类型；CHECK 枚举：\'ORG\',\'DEPT\',\'ANY\'。',
  `holder_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任职者模式；CHECK 枚举：\'SINGLE\',\'MULTIPLE\'。',
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '内置内置；CHECK 枚举：0,1。',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态；CHECK 枚举：\'ENABLED\',\'DISABLED\'。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记；CHECK 枚举：0,1。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_code` (`position_code`),
  KEY `idx_sys_position_status_sort` (`status`,`deleted`,`sort_order`),
  CONSTRAINT `chk_sys_position_flags` CHECK (((`built_in` in (0,1)) and (`deleted` in (0,1)))),
  CONSTRAINT `chk_sys_position_holder_mode` CHECK ((`holder_mode` in (_utf8mb4'SINGLE',_utf8mb4'MULTIPLE'))),
  CONSTRAINT `chk_sys_position_revision` CHECK ((`revision` >= 1)),
  CONSTRAINT `chk_sys_position_status` CHECK ((`status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED'))),
  CONSTRAINT `chk_sys_position_unit_type` CHECK ((`applicable_unit_type` in (_utf8mb4'ORG',_utf8mb4'DEPT',_utf8mb4'ANY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局职务定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position_assignment` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：用户在组织节点担任职务的一段不可覆盖任职事实。 有效区间统一为 [effectiveFrom, effectiveTo)，撤销通过 revoked 字段保留历史，重新任职必须新增记录。',
  `position_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职务ID。',
  `organization_unit_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组织单元ID。',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID。',
  `is_primary` tinyint NOT NULL DEFAULT '0' COMMENT '是否主职；CHECK 枚举：0,1。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `effective_from` datetime(6) NOT NULL COMMENT '生效起始时间。',
  `effective_to` datetime(6) DEFAULT NULL COMMENT '生效结束时间。',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '撤销时间。',
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销人。',
  `revoke_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤销原因。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_assignment_fact` (`position_id`,`organization_unit_id`,`user_id`,`effective_from`),
  KEY `idx_sys_position_assignment_lookup` (`position_id`,`organization_unit_id`,`effective_from`,`effective_to`,`revoked_at`),
  KEY `idx_sys_position_assignment_user` (`user_id`,`effective_from`,`effective_to`,`revoked_at`),
  KEY `idx_sys_position_assignment_unit` (`organization_unit_id`,`position_id`),
  CONSTRAINT `fk_sys_position_assignment_org` FOREIGN KEY (`organization_unit_id`) REFERENCES `sys_organization` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sys_position_assignment_position` FOREIGN KEY (`position_id`) REFERENCES `sys_position` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sys_position_assignment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_sys_position_assignment_period` CHECK (((`effective_to` is null) or (`effective_to` > `effective_from`))),
  CONSTRAINT `chk_sys_position_assignment_primary` CHECK ((`is_primary` in (0,1))),
  CONSTRAINT `chk_sys_position_assignment_revision` CHECK ((`revision` >= 1)),
  CONSTRAINT `chk_sys_position_assignment_revoked_at` CHECK (((`revoked_at` is null) or (`revoked_at` >= `effective_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织职务任职表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position_assignment_batch` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：已成功提交的批量任命幂等结果。',
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。',
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。',
  `assignment_ids_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任职ID集合JSON。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_batch_idempotency` (`created_by`,`idempotency_key`),
  KEY `idx_sys_position_batch_time` (`create_time`),
  CONSTRAINT `fk_sys_position_batch_actor` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_sys_position_batch_hash` CHECK (regexp_like(`request_hash`,_utf8mb4'^[0-9a-f]{64}$')),
  CONSTRAINT `chk_sys_position_batch_result_json` CHECK (json_valid(`assignment_ids_json`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职务批量任命回执表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：角色ID。',
  `role_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称。',
  `role_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码。',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '说明：描述。',
  `sort` int DEFAULT '0' COMMENT '显示排序。',
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0启用 1禁用）。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：删除标志（0正常 1删除）。',
  `sort_order` int DEFAULT '0' COMMENT '排序号。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `role_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色ID。',
  `menu_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单ID。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：用户ID。',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名。',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '昵称。',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码。',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '邮箱。',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '手机号。',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '头像。',
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0启用 1禁用）。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标记：删除标志（0正常 1删除）。',
  `org_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织ID。',
  `dept_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门ID。',
  `password_reset_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必须在继续使用系统前修改密码。',
  `token_version` bigint NOT NULL DEFAULT '0' COMMENT '令牌版本：用户令牌撤销版本；递增后此前签发的会话需按安全校验失效。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_group` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID。',
  `group_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组ID。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_group` (`user_id`,`group_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户用户组关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID。',
  `role_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色ID。',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_operation_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：只追加的系统关键操作审计日志。',
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件ID。',
  `operation_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务操作ID；同一次跨模块操作共享。',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '追踪ID。',
  `parent_operation_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父业务操作ID。',
  `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权威来源系统/模块。',
  `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权威来源记录类型。',
  `source_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权威来源记录ID。',
  `source_event_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权威来源事件ID。',
  `module_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块编码。',
  `operation_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作编码。',
  `operation_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作名称。',
  `risk_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '风险级别。',
  `result` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果。',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID。',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人名称。',
  `operator_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人IP。',
  `user_agent` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent。',
  `request_method` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法。',
  `request_path` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求路径。',
  `target_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标类型。',
  `target_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标ID。',
  `target_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标名称。',
  `summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '摘要。',
  `before_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '之前JSON。',
  `after_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '之后JSON。',
  `changed_fields_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '变更标记字段集合JSON。',
  `payload_truncated` tinyint NOT NULL DEFAULT '0' COMMENT '载荷是否被截断。',
  `error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误编码。',
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误消息。',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_operation_event` (`event_id`),
  KEY `idx_system_operation_created` (`create_time`),
  KEY `idx_system_operation_operator` (`operator_id`,`create_time`),
  KEY `idx_system_operation_module` (`module_code`,`operation_code`,`create_time`),
  KEY `idx_system_operation_target` (`target_type`,`target_id`),
  KEY `idx_system_operation_result` (`result`,`create_time`),
  KEY `idx_system_operation_trace` (`trace_id`),
  KEY `idx_system_operation_operation` (`operation_id`,`create_time`),
  KEY `idx_system_operation_source` (`source_type`,`source_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统关键操作审计表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_sla_escalation_step` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `policy_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略ID。',
  `step_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤名称。',
  `metric_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指标类型。',
  `trigger_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发类型。',
  `offset_minutes` int NOT NULL DEFAULT '0' COMMENT '偏移分钟数。',
  `repeat_interval_minutes` int DEFAULT NULL COMMENT '重复间隔分钟数。',
  `max_executions` int NOT NULL DEFAULT '1' COMMENT '最大执行次数。',
  `action_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作类型。',
  `template_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板编码。',
  `recipient_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '接收人配置JSON。',
  `target_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '目标配置JSON。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  PRIMARY KEY (`id`),
  KEY `idx_task_sla_step_policy` (`policy_id`,`enabled`,`sort_order`),
  CONSTRAINT `chk_task_sla_step_executions` CHECK ((`max_executions` > 0)),
  CONSTRAINT `chk_task_sla_step_repeat` CHECK (((`repeat_interval_minutes` is null) or (`repeat_interval_minutes` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时效提醒升级步骤表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_sla_policy` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `policy_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略编码。',
  `policy_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略名称。',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号。',
  `response_target_minutes` int DEFAULT NULL COMMENT '响应目标分钟数。',
  `completion_target_minutes` int NOT NULL COMMENT '完成目标分钟数。',
  `response_time_basis` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WORKING_TIME' COMMENT '响应时间口径。',
  `completion_time_basis` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WORKING_TIME' COMMENT '完成时间口径。',
  `allow_manual_pause` tinyint NOT NULL DEFAULT '0' COMMENT '允许手动暂停。',
  `pause_on_process_suspend` tinyint NOT NULL DEFAULT '1' COMMENT '暂停时间流程挂起。',
  `max_pause_minutes` int DEFAULT NULL COMMENT '最大暂停分钟数。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_sla_policy_version` (`policy_code`,`version`,`deleted`),
  KEY `idx_task_sla_policy_status` (`policy_code`,`status`,`deleted`,`version`),
  CONSTRAINT `chk_task_sla_policy_completion` CHECK ((`completion_target_minutes` > 0)),
  CONSTRAINT `chk_task_sla_policy_response` CHECK (((`response_target_minutes` is null) or (`response_target_minutes` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户任务时效策略表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_component_template` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：模板ID。',
  `template_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定模板编码。',
  `template_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称。',
  `template_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板类型。',
  `current_version` int NOT NULL DEFAULT '1' COMMENT '当前版本。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_component_template_key` (`template_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI组件模板目录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_component_template_version` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：模板版本ID。',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板ID。',
  `version` int NOT NULL COMMENT '版本号。',
  `snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可变模板快照JSON文档。',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256内容哈希。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：版本说明。',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_component_template_version` (`template_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI组件模板版本表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_hotfix_request` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：HOTFIX申请ID。',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置类型。',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID。',
  `draft_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请时草稿哈希。',
  `active_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请时激活发布ID。',
  `target_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '影响目标摘要。',
  `impact_token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '预检令牌摘要。',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '风险级别。',
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更原因。',
  `ticket_ref` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联工单。',
  `impact_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请时影响预览JSON。',
  `applicant_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请人ID。',
  `applicant_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请人姓名。',
  `window_start` datetime NOT NULL COMMENT '允许发布时间窗口开始。',
  `window_end` datetime NOT NULL COMMENT '允许发布时间窗口结束。',
  `review_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要人工复核：是否需要独立复核；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态。',
  `open_slot` tinyint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'APPROVED',_utf8mb4'PUBLISHING')) then 1 else NULL end)) STORED COMMENT '每个配置仅允许一个开放申请；数据库生成，表达式见本表实现说明。',
  `reviewer_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核人ID；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。',
  `reviewer_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核人名称：复核人姓名；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。',
  `review_comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核意见；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。',
  `reviewed_at` datetime DEFAULT NULL COMMENT '复核时间；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。',
  `release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际发布ID。',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间：实际发布时间。',
  `observation_start` datetime DEFAULT NULL COMMENT '观察窗口开始。',
  `observation_end` datetime DEFAULT NULL COMMENT '观察窗口结束。',
  `observation_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '观察状态。',
  `rolled_back_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回滚人ID。',
  `rolled_back_at` datetime DEFAULT NULL COMMENT '回滚时间。',
  `rollback_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回滚原因。',
  `cancelled_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '取消人ID。',
  `cancelled_at` datetime DEFAULT NULL COMMENT '取消时间。',
  `cancel_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '取消原因。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_hotfix_request_open` (`config_type`,`config_id`,`open_slot`),
  UNIQUE KEY `uk_ui_hotfix_request_release` (`release_id`),
  KEY `idx_ui_hotfix_request_status` (`status`,`window_end`),
  KEY `idx_ui_hotfix_request_config` (`config_type`,`config_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI热修复发布与观察记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_hotfix_target` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：热修复目标ID。',
  `hotfix_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '热修复发布ID。',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置类型。',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID。',
  `process_version_history_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标流程发布历史ID。',
  `pinned_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标原始钉定发布ID。',
  `pinned_release_version` int NOT NULL COMMENT '目标原始钉定版本号。',
  `previous_target_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上一有效热修复目标ID。',
  `effective_snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标有效完整快照JSON文档。',
  `effective_content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标有效快照SHA-256。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态。',
  `active_slot` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED COMMENT '保证同一流程版本只有一个有效目标；数据库生成，表达式见本表实现说明。',
  `activated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '激活人。',
  `activated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '激活时间。',
  `rolled_back_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤回人。',
  `rolled_back_at` datetime DEFAULT NULL COMMENT '撤回时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_hotfix_target_active` (`config_type`,`config_id`,`process_version_history_id`,`active_slot`),
  KEY `idx_ui_hotfix_target_release` (`hotfix_release_id`,`status`),
  KEY `idx_ui_hotfix_target_pinned` (`pinned_release_id`,`status`),
  KEY `idx_ui_hotfix_target_process` (`process_version_history_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI热修复目标快照表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_release` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：发布快照ID。',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置类型。',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单或列表配置ID。',
  `version` int NOT NULL COMMENT '版本号：不可变版本号。',
  `snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '完整运行时快照JSON文档。',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256内容哈希。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INACTIVE' COMMENT '状态。',
  `active_slot` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED COMMENT '保证同一配置只有一个激活版本；数据库生成，表达式见本表实现说明。',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明：发布说明。',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人。',
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间。',
  `release_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD' COMMENT '发布模式。',
  `base_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热修复基线发布ID。',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SAFE' COMMENT '风险级别。',
  `rollout_scope` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布策略范围。',
  `patch_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '稳定ID语义补丁JSON文档。',
  `override_risk` tinyint NOT NULL DEFAULT '0' COMMENT '是否经授权覆盖REVIEW风险。',
  `override_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险覆盖原因。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_config_release_version` (`config_type`,`config_id`,`version`),
  UNIQUE KEY `uk_ui_config_release_active` (`config_type`,`config_id`,`active_slot`),
  KEY `idx_ui_config_release_active` (`config_type`,`config_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单列表发布快照表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_release_audit` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：审计ID。',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置类型。',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID。',
  `release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联发布ID。',
  `operation` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作。',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险级别。',
  `actor_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID。',
  `actor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人名称。',
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布或覆盖原因。',
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务追踪ID。',
  `detail_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '影响范围与差异JSON文档。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  KEY `idx_ui_release_audit_config` (`config_type`,`config_id`,`create_time`),
  KEY `idx_ui_release_audit_release` (`release_id`,`create_time`),
  KEY `idx_ui_release_audit_operation` (`operation`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI发布审计表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_event_binding` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：事件绑定链ID。',
  `owner_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所有者类型。',
  `owner_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '持有者ID：所属实体、表单或列表ID。',
  `target_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OWNER' COMMENT '目标类型。',
  `target_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '目标标识：字段节点或按钮稳定编码，OWNER为空串。',
  `event_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统一业务事件编码。',
  `inheritance_mode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INHERIT' COMMENT '继承模式。',
  `steps_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '步骤集合文档：BEFORE/REPLACE/AFTER有序步骤JSON数组。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：草稿修订号。',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_event_binding_scope` (`owner_type`,`owner_id`,`target_type`,`target_key`,`event_code`,`deleted`),
  KEY `idx_ui_event_binding_owner` (`owner_type`,`owner_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一UI事件绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_extension_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：扩展定义ID。',
  `extension_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '扩展类型：本表取值为 `FORM`/`NODE`/`FIELD`/`LIST`/`INTERFACE`；管理层展示时把前四类投影为 `UI_*`，并聚合另表中的流程动作和人员解析器。',
  `extension_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '扩展稳定编码：完整能力的唯一稳定编码。',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名称。',
  `version` int NOT NULL COMMENT '版本号：扩展实现版本。',
  `snapshot_version` int NOT NULL DEFAULT '1' COMMENT '配置快照协议版本。',
  `visibility_scope` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GLOBAL' COMMENT '适用范围：GLOBAL/ENTITY。',
  `entity_codes_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '指定适用实体编码JSON数组。',
  `supported_modes_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '支持的运行模式JSON数组。',
  `supported_node_types_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '支持的节点类型JSON数组。',
  `supported_bindings_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '支持的绑定类型JSON数组。',
  `config_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '配置Schema JSON文档。',
  `capabilities_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '扩展能力声明JSON文档。',
  `implementation_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口实现类型：字典、静态数据、Provider、运行时上下文或结构化计算。',
  `provider_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Provider编码：已注册 Provider 的稳定编码。',
  `scope_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口作用域：`GLOBAL`/`ENTITY`/`FORM`/`LIST`。',
  `scope_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域资源ID：非全局接口对应的实体、表单或列表 ID。',
  `implementation_config_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '接口实现配置：受控实现配置 JSON。',
  `execution_policy_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '接口执行策略：超时、缓存、失败回退等策略 JSON。',
  `input_schema_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '输入 Schema：接口输入 JSON Schema。',
  `output_schema_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '输出 Schema：接口输出 JSON Schema。',
  `interface_kind` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口读写类型：`READ`/`WRITE`。',
  `interface_context_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调用上下文：`FORM`/`LIST`/`ENTITY`。',
  `provider_operation_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Provider内部路由：仅供后端实现分派，不是设计器的第二层选项。',
  `legacy_service_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '历史服务ID：仅用于不可变历史快照兼容，新契约不输出。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：定义修订号。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_extension_version` (`extension_type`,`extension_key`,`version`,`deleted`),
  UNIQUE KEY `uk_ui_extension_legacy_interface` (`legacy_service_id`,`provider_operation_code`,`deleted`),
  KEY `idx_ui_extension_catalog` (`extension_type`,`extension_key`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一扩展定义目录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_hotfix_observation_metric` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：指标ID。',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'HOTFIX申请ID。',
  `release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'HOTFIX发布ID。',
  `metric_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指标编码。',
  `total_count` bigint NOT NULL DEFAULT '0' COMMENT '观察总次数。',
  `failure_count` bigint NOT NULL DEFAULT '0' COMMENT '失败次数。',
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近失败摘要。',
  `last_observed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近观察时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_hotfix_observation_metric` (`request_id`,`metric_code`),
  KEY `idx_ui_hotfix_observation_release` (`release_id`,`metric_code`),
  CONSTRAINT `fk_ui_hotfix_metric_request` FOREIGN KEY (`request_id`) REFERENCES `ui_config_hotfix_request` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI热修复观察指标表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_view_composition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：表单或列表设计器中的“关联内容”草稿记录。 目标内容、关联方式、允许操作和特殊处理统一保存在经过严格校验的 config_document 中；独立记录使关联内容可以稳定排序、乐观并发更新， 并能作为宿主发布快照的一部分参与差异比较和撤销。',
  `owner_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所有者类型；CHECK 枚举：\'FORM\', \'LIST\'。',
  `owner_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '持有者ID：表单或列表配置ID。',
  `composition_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '宿主内稳定业务标识。',
  `anchor_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OWNER' COMMENT '锚点类型。',
  `anchor_key` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '非OWNER挂载点的稳定标识。',
  `config_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '经白名单校验的关联内容配置JSON。',
  `order_key` bigint NOT NULL DEFAULT '1000' COMMENT '同一挂载点内的稳定排序键。',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号：乐观锁修订号。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记；CHECK 枚举：0, 1。',
  `active_composition_key` varchar(100) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`deleted` = 0) then `composition_key` else NULL end)) STORED COMMENT '仅活动记录参与宿主内编码唯一约束；数据库生成，表达式见本表实现说明。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_view_composition_active_key` (`owner_type`,`owner_id`,`active_composition_key`),
  KEY `idx_ui_view_composition_owner_order` (`owner_type`,`owner_id`,`deleted`,`order_key`),
  CONSTRAINT `chk_ui_view_composition_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_ui_view_composition_owner` CHECK ((`owner_type` in (_utf8mb4'FORM',_utf8mb4'LIST'))),
  CONSTRAINT `chk_ui_view_composition_revision` CHECK ((`revision` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单列表关联内容表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_calendar` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `calendar_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日历编码。',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日历名称。',
  `timezone_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '时区ID。',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号。',
  `default_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认日历。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态。',
  `effective_from` date DEFAULT NULL COMMENT '生效起始时间。',
  `effective_to` date DEFAULT NULL COMMENT '生效结束时间。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_code_version` (`calendar_code`,`version`,`deleted`),
  KEY `idx_work_calendar_status` (`status`,`default_flag`,`deleted`),
  CONSTRAINT `chk_work_calendar_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作日历表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_calendar_binding` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `scope_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '范围类型。',
  `scope_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '范围键。',
  `calendar_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日历ID。',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级。',
  `effective_from` date DEFAULT NULL COMMENT '生效起始时间。',
  `effective_to` date DEFAULT NULL COMMENT '生效结束时间。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态。',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记。',
  PRIMARY KEY (`id`),
  KEY `idx_work_calendar_binding_scope` (`scope_type`,`scope_key`,`status`,`deleted`,`priority`),
  KEY `idx_work_calendar_binding_calendar` (`calendar_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作日历作用域绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_calendar_exception` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `calendar_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日历ID。',
  `exception_date` date NOT NULL COMMENT '例外日期。',
  `exception_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '例外类型。',
  `exception_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '例外名称。',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_exception` (`calendar_id`,`exception_date`),
  KEY `idx_work_calendar_exception_date` (`calendar_id`,`exception_date`,`exception_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日历特殊日期表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_calendar_exception_period` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `exception_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '例外ID。',
  `start_minute` smallint NOT NULL COMMENT '起始分钟。',
  `end_minute` smallint NOT NULL COMMENT '结束分钟。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_exception_period` (`exception_id`,`start_minute`,`end_minute`),
  KEY `idx_work_calendar_exception_period` (`exception_id`,`sort_order`),
  CONSTRAINT `chk_work_calendar_exception_period_minutes` CHECK (((`start_minute` >= 0) and (`end_minute` <= 1440) and (`start_minute` < `end_minute`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特殊日期工作时段表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_calendar_period` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `calendar_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日历ID。',
  `day_of_week` tinyint NOT NULL COMMENT '星期。',
  `start_minute` smallint NOT NULL COMMENT '起始分钟。',
  `end_minute` smallint NOT NULL COMMENT '结束分钟。',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_period` (`calendar_id`,`day_of_week`,`start_minute`,`end_minute`),
  KEY `idx_work_calendar_period_day` (`calendar_id`,`day_of_week`,`sort_order`),
  CONSTRAINT `chk_work_calendar_period_day` CHECK ((`day_of_week` between 1 and 7)),
  CONSTRAINT `chk_work_calendar_period_minutes` CHECK (((`start_minute` >= 0) and (`end_minute` <= 1440) and (`start_minute` < `end_minute`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每周工作时段表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_bootstrap_job` (
  `job_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称。',
  `completed_version` int NOT NULL DEFAULT '0' COMMENT '完成版本。',
  `owner_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。',
  `completed_at` datetime(6) DEFAULT NULL COMMENT '完成时间。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间。',
  PRIMARY KEY (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='启动初始化任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_outbox_event` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID：通用数据库 Outbox 持久化记录。',
  `topic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题。',
  `event_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件键。',
  `aggregate_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '聚合根类型。',
  `aggregate_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '聚合根ID。',
  `payload_document` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '载荷文档。',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态。',
  `owner_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。',
  `lease_token` bigint NOT NULL DEFAULT '0' COMMENT '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试数量。',
  `max_retries` int NOT NULL DEFAULT '8' COMMENT '最大重试次数。',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下一次重试时间。',
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误消息。',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间。',
  `processed_time` datetime DEFAULT NULL COMMENT '处理时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_outbox_topic_event` (`topic`,`event_key`),
  KEY `idx_workflow_outbox_ready` (`status`,`next_retry_time`,`create_time`),
  KEY `idx_workflow_outbox_aggregate` (`aggregate_type`,`aggregate_id`),
  KEY `idx_workflow_outbox_lease` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务发件箱事件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_schema_change` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID。',
  `ddl_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DDL哈希。',
  `active_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '活跃结构请求哈希。',
  `ddl_statement` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DDL语句。',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态。',
  `attempt` int NOT NULL DEFAULT '0' COMMENT '尝试次数。',
  `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。',
  `lease_token` bigint NOT NULL DEFAULT '0' COMMENT '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。',
  `next_attempt_at` datetime(6) NOT NULL COMMENT '下一次尝试次数时间。',
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近错误。',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间。',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。',
  `completed_time` datetime(6) DEFAULT NULL COMMENT '完成时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schema_change_active_hash` (`active_hash`),
  KEY `idx_schema_change_claim` (`status`,`next_attempt_at`,`lease_until`,`create_time`),
  KEY `idx_schema_change_hash` (`ddl_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库结构执行队列表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- 系统级初始数据：仅保留当前版本实际使用的默认目录、权限与管理员。
SET @INIT_OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS=0;

-- Flowable 通用资源引擎属性表，保存引擎内部状态和关联数据的系统初始记录。
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('app.schema.version','7.2.0.2',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('cfg.execution-related-entities-count','true',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('cfg.task-related-entities-count','true',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('cmmn.schema.version','7.2.0.2',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('common.schema.version','7.2.0.2',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('dmn.schema.version','7.2.0.2',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('eventregistry.schema.version','7.2.0.2',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('next.dbid','1',1);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('schema.history','upgrade(6.8.0.0->7.2.0.2)',2);
INSERT INTO `ACT_GE_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('schema.version','7.2.0.2',2);

-- Flowable 身份管理引擎属性表，保存引擎内部状态和关联数据的系统初始记录。
INSERT INTO `ACT_ID_PROPERTY` (`NAME_`, `VALUE_`, `REV_`) VALUES ('schema.version','7.2.0.2',1);

-- 实体定义表的系统初始记录。
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (315408474423554179,'sys_dict','字典类型','平台系统表目录：sys_dict',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (319550136508222867,'sys_menu','菜单权限','平台系统表目录：sys_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (429188770482109985,'sys_user','系统用户','平台系统表目录：sys_user',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (540477245371031459,'sys_role_menu','角色菜单关系','平台系统表目录：sys_role_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (543966161995942233,'sys_user_role','用户角色关系','平台系统表目录：sys_user_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (687980787977785014,'sys_dict_item','字典明细','平台系统表目录：sys_dict_item',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict_item','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (695805941702569049,'sys_role','系统角色','平台系统表目录：sys_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (704307435534855320,'sys_user_group','用户组成员关系','平台系统表目录：sys_user_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (869084506871349004,'sys_organization','组织部门','平台系统表目录：sys_organization',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_organization','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (924525185085388686,'sys_group','用户组','平台系统表目录：sys_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');

-- 实体字段定义表的系统初始记录。
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (302983935847141633,315408474423554179,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (463024980901796770,315408474423554179,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (349900140693140919,315408474423554179,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (668047102303117425,315408474423554179,'dict_code','字典编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (683413866831122108,315408474423554179,'dict_name','字典名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (660514776571799460,315408474423554179,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1041515214726648952,315408474423554179,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (835260617712650971,315408474423554179,'status','状态：0-启用 1-禁用','STRING','char(1)',1,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (392848417810309094,315408474423554179,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (343176658012303195,319550136508222867,'breadcrumb','是否显示面包屑：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'breadcrumb',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (558990094873955059,319550136508222867,'component','组件路径','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'component',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (175753738162967087,319550136508222867,'create_by','创建者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (950655553882382674,319550136508222867,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (205676883257074073,319550136508222867,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (138193370562438585,319550136508222867,'entity_code','关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,23,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'entity_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (407584881042156273,319550136508222867,'icon','菜单图标','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'icon',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (58574189954682540,319550136508222867,'id','菜单ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1138396868793173976,319550136508222867,'is_cache','是否缓存：0-缓存 1-不缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,21,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_cache',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (776238513957782207,319550136508222867,'is_frame','是否外链：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,20,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_frame',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (111764993240200811,319550136508222867,'keep_alive','是否缓存：0-不缓存 1-缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'keep_alive',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (119578562633124551,319550136508222867,'list_key','实体列表稳定编码','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,25,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'list_key',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1002235088452601204,319550136508222867,'menu_name','菜单名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (198475160699508098,319550136508222867,'menu_type','菜单类型：M-目录 C-菜单 F-按钮','STRING','char(1)',1,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (573255979784869818,319550136508222867,'parent_id','父菜单ID，0为顶级菜单','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (426434390452136951,319550136508222867,'path','路由地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (908207397228493680,319550136508222867,'perm','权限标识，如：system:user:list','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'perm',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (544552761361520586,319550136508222867,'query','路由参数','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,22,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'query',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (158131531689092062,319550136508222867,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (509277996761328404,319550136508222867,'resource_type','菜单资源类型，ENTITY_LIST 表示动态实体列表','STRING','varchar(30)',30,0,0,NULL,NULL,NULL,24,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'resource_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (323682773251959664,319550136508222867,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (628997735940863871,319550136508222867,'status','状态：0-禁用 1-启用','STRING','char(1)',1,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (658300988401276707,319550136508222867,'update_by','更新者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (531863267937602399,319550136508222867,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,19,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (555037528202687605,319550136508222867,'visible','显示状态：0-隐藏 1-显示','STRING','char(1)',1,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'visible',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (852378610239470992,429188770482109985,'avatar','头像','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'avatar',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (23541322679314285,429188770482109985,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (361679855788775973,429188770482109985,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (10101577987500861,429188770482109985,'dept_id','部门ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dept_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (326932484964000364,429188770482109985,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1134826313312664922,429188770482109985,'id','用户ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (745051415557763972,429188770482109985,'nickname','昵称','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'nickname',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1148301704392575944,429188770482109985,'org_id','组织ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (317101019809934504,429188770482109985,'password','密码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'password',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1039751570117789095,429188770482109985,'password_reset_required','password_reset_required','BOOLEAN','tinyint',NULL,1,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'password_reset_required',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (475192751198870445,429188770482109985,'phone','手机号','STRING','varchar(20)',20,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (59576534536847010,429188770482109985,'status','状态（0启用 1禁用）','STRING','char(1)',1,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (42152131250552875,429188770482109985,'token_version','Incremented to revoke all previously issued sessions','LONG','bigint',NULL,1,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'token_version',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (736587934404834291,429188770482109985,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (184566779876080086,429188770482109985,'username','用户名','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'username',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (761157259593380615,540477245371031459,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (480775900607645235,540477245371031459,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (561900151098621684,540477245371031459,'menu_id','菜单ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (919996913633728290,540477245371031459,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (979647368025597245,543966161995942233,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (728367720794403925,543966161995942233,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (75963321277264675,543966161995942233,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1082238976243929409,543966161995942233,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (290109203980610604,687980787977785014,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (902908459287977551,687980787977785014,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1112820714573490757,687980787977785014,'dict_code','冗余：字典编码（便于直接查询）','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (845110899938467101,687980787977785014,'dict_id','所属字典ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (85937474892350588,687980787977785014,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (978970058829541345,687980787977785014,'item_code','项编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (458794463769860611,687980787977785014,'item_label','项标签/显示文本','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_label',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (293542286014999959,687980787977785014,'item_value','项值','STRING','varchar(200)',200,1,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_value',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (160051157971634527,687980787977785014,'parent_id','父项ID，0表示顶级','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1042513892081176326,687980787977785014,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (290497113002851889,687980787977785014,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (23540013034190091,687980787977785014,'status','状态：0-启用 1-禁用','STRING','char(1)',1,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (541437194327279494,687980787977785014,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (125004887893384772,695805941702569049,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (50302947400312544,695805941702569049,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (464945732470729299,695805941702569049,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (39605060466058778,695805941702569049,'id','角色ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (582801574103340315,695805941702569049,'role_code','角色编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (846037797095146960,695805941702569049,'role_name','角色名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (469870220014538885,695805941702569049,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (511818241961063640,695805941702569049,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (249397106466806541,695805941702569049,'status','状态（0启用 1禁用）','STRING','char(1)',1,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (990484312511420209,695805941702569049,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (106998218957706418,704307435534855320,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (957745245096590288,704307435534855320,'group_id','组ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (460851437342127427,704307435534855320,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1137657801046065632,704307435534855320,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (933634782907475287,869084506871349004,'address','地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'address',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (730434704510267566,869084506871349004,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (79125910432577094,869084506871349004,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (78753772494339085,869084506871349004,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (378782243158581413,869084506871349004,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (971589645354457971,869084506871349004,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (978131954777805791,869084506871349004,'leader_id','负责人ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (507492914926553510,869084506871349004,'leader_name','负责人名称（冗余）','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (538220663657061167,869084506871349004,'level','层级（0为顶级）','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'level',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (49047951767170137,869084506871349004,'org_code','组织编码（唯一）','STRING','varchar(100)',100,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (103503443431748381,869084506871349004,'org_name','组织名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (756767862978652736,869084506871349004,'parent_id','父级ID（顶级为0）','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (689704407809393946,869084506871349004,'path','完整路径，如：/0/1/5/10/','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (34676674937238359,869084506871349004,'phone','联系电话','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (799314663885053329,869084506871349004,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1046028949155177888,869084506871349004,'status','状态：0-启用，1-禁用','STRING','varchar(10)',10,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (46123295334207598,869084506871349004,'type','类型：org-组织，dept-部门','STRING','varchar(20)',20,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (923457855008240877,869084506871349004,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (221052828124990695,924525185085388686,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (276971412500557125,924525185085388686,'deleted','删除标志','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (147276096293704262,924525185085388686,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (954151322232061889,924525185085388686,'group_code','组编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (324844729973447637,924525185085388686,'group_name','组名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1039028238930600397,924525185085388686,'id','组ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (22985037036471449,924525185085388686,'parent_id','父组ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (332274980659204946,924525185085388686,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (836913778987896460,924525185085388686,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (370552990036906948,924525185085388686,'status','状态（0启用 1禁用）','STRING','char(1)',1,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `ref_field_code`, `ref_list_key`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (335370829110629664,924525185085388686,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);

-- 流程动作处理器目录表的系统初始记录。
INSERT INTO `process_action_definition` (`id`, `action_code`, `display_name`, `description`, `handler_name`, `visibility_scope`, `enabled`, `created_by`, `create_time`, `update_time`, `deleted`) VALUES ('flow_action_definition_notify','sendNotificationHandler','发送流程通知','发送待办、完成、撤回等流程通知；推荐使用提交后执行。','sendNotificationHandler','GLOBAL',1,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);

-- 受控人员解析器目录表的系统初始记录。
INSERT INTO `process_person_resolver_definition` (`id`, `resolver_code`, `display_name`, `description`, `bean_name`, `implementation_version`, `contract_version`, `supported_usages_document`, `extra_param_schema_document`, `dynamic_extra_params`, `enabled`, `revision`, `create_time`, `update_time`, `deleted`) VALUES ('person_resolver_entity_user_reference_001','entityUserReferenceField','实体用户关系字段','从流程绑定实体的已发布用户单选或多选关系字段读取人员','entityUserReferenceFieldPersonResolver',1,1,'[\"ASSIGNEE\",\"CANDIDATE\",\"MULTI_INSTANCE\"]','{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"schemaVersion\",\"entityCode\",\"fieldCode\"],\"properties\":{\"schemaVersion\":{\"const\":1},\"entityCode\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":128},\"fieldCode\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":100,\"pattern\":\"^[A-Za-z][A-Za-z0-9_]{0,99}$\"}}}',0,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO `process_person_resolver_definition` (`id`, `resolver_code`, `display_name`, `description`, `bean_name`, `implementation_version`, `contract_version`, `supported_usages_document`, `extra_param_schema_document`, `dynamic_extra_params`, `enabled`, `revision`, `create_time`, `update_time`, `deleted`) VALUES ('person_resolver_relative_position_001','relativeOrgPosition','相对组织职务','按流程发起人冻结组织链查询节点激活时的有效职务任职人','relativeOrgPositionPersonResolver',1,1,'[\"CANDIDATE\",\"ASSIGNEE\",\"MULTI_INSTANCE\"]','{\"multipleMatchPolicies\":[\"ERROR\",\"PRIMARY_OR_ERROR\",\"ALL\"],\"subject\":[\"PROCESS_INITIATOR\"],\"schemaVersion\":1,\"lookupModes\":[\"SELF\",\"FIXED_ANCESTOR\",\"NEAREST_WITH_HOLDER\",\"BUSINESS_LEVEL\"],\"anchor\":[\"DEPARTMENT\",\"ORGANIZATION\"],\"type\":\"object\"}',0,1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);

-- 系统字典类型表的系统初始记录。
INSERT INTO `sys_dict` (`id`, `dict_code`, `dict_name`, `description`, `status`, `sort`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_business_level_001','organization_business_level','组织业务层级','独立于物理树深度的稳定组织业务层级','0',10,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- 系统字典明细表的系统初始记录。
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `dict_code`, `parent_id`, `item_code`, `item_label`, `item_value`, `sort`, `status`, `remark`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_level_center_001','dict_org_business_level_001','organization_business_level','0','CENTER','中心','CENTER',30,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `dict_code`, `parent_id`, `item_code`, `item_label`, `item_value`, `sort`, `status`, `remark`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_level_company_001','dict_org_business_level_001','organization_business_level','0','COMPANY','公司','COMPANY',20,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `dict_code`, `parent_id`, `item_code`, `item_label`, `item_value`, `sort`, `status`, `remark`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_level_dept1_001','dict_org_business_level_001','organization_business_level','0','FIRST_LEVEL_DEPT','一级部门','FIRST_LEVEL_DEPT',40,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `dict_code`, `parent_id`, `item_code`, `item_label`, `item_value`, `sort`, `status`, `remark`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_level_dept2_001','dict_org_business_level_001','organization_business_level','0','SECOND_LEVEL_DEPT','二级部门','SECOND_LEVEL_DEPT',50,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `dict_code`, `parent_id`, `item_code`, `item_label`, `item_value`, `sort`, `status`, `remark`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_level_group_001','dict_org_business_level_001','organization_business_level','0','GROUP','集团','GROUP',10,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `dict_code`, `parent_id`, `item_code`, `item_label`, `item_value`, `sort`, `status`, `remark`, `deleted`, `create_time`, `update_time`) VALUES ('dict_org_level_team_001','dict_org_business_level_001','organization_business_level','0','TEAM','团队','TEAM',60,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- 全局设置与个人偏好表的系统初始记录。
INSERT INTO `sys_global_setting` (`id`, `scope_type`, `owner_id`, `setting_key`, `name`, `setting_value_type`, `setting_value`, `remark`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`) VALUES ('setting_entity_field_types','SYSTEM','0','ui.entity_design.field_types_collapsed','实体设计字段类型面板收起状态','BOOLEAN','false','true 表示收起，false 表示展开，默认展开。同一账号在所有实体设计页共用；用户设置优先于系统设置，删除个人记录后恢复继承。切换状态自动保存，不影响实体未保存状态和发布。',0,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_global_setting` (`id`, `scope_type`, `owner_id`, `setting_key`, `name`, `setting_value_type`, `setting_value`, `remark`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`) VALUES ('setting_layout_sidebar_collapsed','SYSTEM','0','ui.layout.sidebar_collapsed','左侧主菜单收起状态','BOOLEAN','false','true 表示收起，false 表示展开，默认展开。未保存个人偏好时使用系统设置；用户手动切换后自动保存个人偏好，同一账号跨页面和浏览器共用，用户配置优先于系统配置。仅控制桌面主菜单，移动端导航抽屉不受影响。',0,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO `sys_global_setting` (`id`, `scope_type`, `owner_id`, `setting_key`, `name`, `setting_value_type`, `setting_value`, `remark`, `version`, `created_by`, `updated_by`, `create_time`, `update_time`) VALUES ('setting_layout_tabs_enabled','SYSTEM','0','ui.layout.tabs_enabled','启用顶部多标签页','BOOLEAN','false','true 表示在顶部面包屑位置显示页面选项卡，false 表示单页模式并显示面包屑，默认关闭。用户可在右上角用户菜单切换，个人配置优先于系统配置。切换标签保留页面状态，关闭未保存页面时确认；已打开标签和业务输入仅保留在当前会话内，刷新页面后不恢复。偏好保存失败不影响本次模式切换。',0,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

-- 菜单与功能权限表的系统初始记录。
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('300','0','配置管理','M','Box',3,'/config',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('400','0','系统管理','M','Setting',4,'/system',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('403','300','流程用户组','C','FolderOpened',3,'/config/process-user-groups',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('assignee_incident_handle_001','assignee_incident_menu_001','处置空办理人事件','F',NULL,1,'','','process:assignee-incident:handle','0','0','0','1','补充办理人、重试解析器、转兜底组或终止实例',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('assignee_incident_menu_001','0','空办理人事件','C','Warning',78,'/system/assignee-incidents','system/AssigneeIncidentManagement','process:assignee-incident:list','0','0','0','1','查看空办理人告警、重试和人工恢复记录',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_analyze_001','config_migration_menu_001','分析配置','F',NULL,4,'','','config-migration:analyze','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_download_001','config_migration_menu_001','下载发布包','F',NULL,2,'','','config-migration:download','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_export_001','config_migration_menu_001','导出配置','F',NULL,1,'','','config-migration:export','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_import_001','config_migration_menu_001','导入配置','F',NULL,3,'','','config-migration:import','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_list_001','config_migration_menu_001','查看配置迁移','F',NULL,0,'','','config-migration:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_menu_001','400','配置迁移','C','FolderOpened',90,'/system/config-migration','system/ConfigMigration','config-migration:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_publish_001','config_migration_menu_001','发布配置','F',NULL,5,'','','config-migration:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_rollback_001','config_migration_menu_001','回滚配置','F',NULL,6,'','','config-migration:rollback','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('custom_form_guide','flow_setting_menu_001','自定义表单组件','C','Document',4,'/dev/manual/custom-form','/views/system/CustomFormGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('custom_list_guide','flow_setting_menu_001','自定义列表组件','C','Document',3,'/dev/manual/custom-list','/views/system/CustomListGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('dev_guide_dir','0','定制开发','M','Document',5,'/dev',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('dev_guide_list','flow_setting_menu_001','列表字段扩展','C','Document',1,'/dev/manual/list-field-extension','/views/system/DevGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('embed_management_menu_001','0','嵌入集成','C','Monitor',76,'/system/embed-management','system/EmbedManagement','system:embed:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('embed_perm_identity_manage','embed_management_menu_001','维护嵌入身份映射','F',NULL,4,NULL,NULL,'system:embed:identity-manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('embed_perm_manage','embed_management_menu_001','维护嵌入视图与授权','F',NULL,2,NULL,NULL,'system:embed:manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('embed_perm_publish','embed_management_menu_001','发布嵌入视图','F',NULL,3,NULL,NULL,'system:embed:publish','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('embed_perm_session_revoke','embed_management_menu_001','撤销嵌入会话','F',NULL,5,NULL,NULL,'system:embed:session-revoke','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('embed_perm_view','embed_management_menu_001','查看嵌入运行时','F',NULL,1,NULL,NULL,'system:embed:view','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_scope_explicit_all_permission_001','0','允许全量数据放行','F',NULL,0,'','','entity:list-scope:explicit-all','0','1','0','1','允许在列表数据范围配置中显式确认全量可见的高风险权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_ui_hotfix_observe_permission','0','UI热修复观察指标','F',NULL,94,NULL,NULL,'entity:ui-config:hotfix:observe','0','1','0','1','HOTFIX运行观察指标上报',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_ui_hotfix_override_permission','300','UI配置热修复风险覆盖','F',NULL,91,NULL,NULL,'entity:ui-config:hotfix:override','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_ui_hotfix_publish_permission','300','UI配置兼容热修复','F',NULL,90,NULL,NULL,'entity:ui-config:hotfix','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_ui_hotfix_rollback_permission','0','UI热修复受控回滚','F',NULL,93,NULL,NULL,'entity:ui-config:hotfix:rollback','0','1','0','1','HOTFIX专用回滚权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_config_list_001','entity_version_management_001','查看数据版本配置','F',NULL,1,'','','entity:version:config:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_config_publish_001','entity_version_management_001','发布数据版本配置','F',NULL,3,'','','entity:version:config:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_config_update_001','entity_version_management_001','维护数据版本配置','F',NULL,2,'','','entity:version:config:update','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_management_001','0','数据版本','C','Clock',74,'/system/entity-versions','system/EntityVersionManagement','entity:version:config:list','0','0','0','1','实体数据版本策略、发布与比较',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_record_capture_001','entity_version_management_001','手工固化记录版本','F',NULL,5,'','','entity:version:record:capture','0','0','0','1','手工生成记录检查点',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_record_view_001','entity_version_management_001','查看记录版本','F',NULL,4,'','','entity:version:record:view','0','0','0','1','查看有数据权限的记录历史版本',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_list_permission_001','extension_management_menu_001','查看扩展','F',NULL,1,'','','system:extension:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_management_menu_001','dev_guide_dir','扩展管理','C','Setting',2,'/dev/extensions','system/ExtensionManagement',NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_test_permission_001','extension_management_menu_001','测试扩展','F',NULL,3,'','','system:extension:test','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_update_permission_001','extension_management_menu_001','维护扩展','F',NULL,2,'','','system:extension:update','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('external_system_manage_permission_001','external_system_menu_001','维护外部系统','F',NULL,2,'','','system:external-system:manage','0','0','0','1','新增、编辑、启停和删除外部系统及其参数',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('external_system_menu_001','400','外部系统','C','Connection',14,'/system/external-systems','system/ExternalSystem',NULL,'0','0','0','1','维护外部系统基础资料和对接参数；具体接口由业务扩展实现',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('external_system_view_permission_001','external_system_menu_001','查看外部系统','F',NULL,1,'','','system:external-system:view','0','0','0','1','查看外部系统基本信息列表',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('flow_action_guide_menu_001','flow_setting_menu_001','流程动作','C','Notebook',5,'/dev/manual/flow-actions','system/FlowActionGuide','system:flowAction:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('flow_setting_menu_001','dev_guide_dir','开发手册','M','Notebook',1,'/dev/manual',NULL,NULL,'0','0','0','1','定制开发相关配置与扩展手册',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('global_settings_manage','global_settings_menu','维护全局设置','F',NULL,2,'','','system:setting:manage','0','0','0','1','修改或恢复系统默认值',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('global_settings_menu','400','全局设置','C','Setting',15,'/system/settings','system/GlobalSettings',NULL,'0','0','0','1','设置系统默认值，用户个人偏好可单独覆盖',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('global_settings_view','global_settings_menu','查看全局设置','F',NULL,1,'','','system:setting:view','0','0','0','1','查看系统设置及逻辑说明',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('integration_management_menu_001','0','开放集成','C','Connection',75,'/system/open-integration','system/OpenIntegration','system:integration:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('integration_perm_manage','integration_management_menu_001','维护接入应用','F',NULL,2,NULL,NULL,'system:integration:manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('integration_perm_secret_rotate','integration_management_menu_001','轮换应用凭据','F',NULL,3,NULL,NULL,'system:integration:secret-rotate','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('integration_perm_view','integration_management_menu_001','查看开放集成','F',NULL,1,NULL,NULL,'system:integration:view','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('list_column_template_manage_001','list_column_template_menu_001','维护列表列模板','F',NULL,1,'','','system:list-column-template:manage','0','0','0','1','新增、编辑和复制列表列模板',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('list_column_template_menu_001','300','列表列模板','C','Grid',4,'/config/list-column-templates','system/ListColumnTemplateManagement','system:list-column-template:view','0','0','0','1','可视化维护用于一次性初始化的列表列模板',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('list_field_guide_v2_001','flow_setting_menu_001','列表字段扩展2','C','Notebook',2,'/dev/manual/list-field-extension-v2','system/ListFieldExtensionGuide','system:dev:list','0','0','0','1','列表字段扩展开发手册：单元格组件、虚拟列与 ListFieldDataProvider',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('position_assign_permission_001','position_management_menu_001','维护组织任职','F',NULL,2,'','','system:position:assign','0','0','0','1','任命、转任、撤销和调整任职有效期',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('position_manage_permission_001','position_management_menu_001','维护职务定义','F',NULL,1,'','','system:position:manage','0','0','0','1','新增、编辑、启停职务定义',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('position_management_menu_001','400','职务管理','C','Briefcase',3,'/system/position','system/Position','system:position:view','0','0','0','1','全局职务定义与组织任职管理',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_dict_manage','0','Manage dictionaries','F',NULL,0,NULL,NULL,'system:dictionary:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_dict_view','0','View dictionaries','F',NULL,0,NULL,NULL,'system:dictionary:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_entity_manage','0','Manage entity definitions','F',NULL,0,NULL,NULL,'entity:definition:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_entity_publish','0','Publish entity definitions','F',NULL,0,NULL,NULL,'entity:definition:publish','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_entity_view','0','View entity definitions','F',NULL,0,NULL,NULL,'entity:definition:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_file_delete','0','Delete files','F',NULL,0,NULL,NULL,'storage:file:delete','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_file_read','0','Read files','F',NULL,0,NULL,NULL,'storage:file:read','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_file_write','0','Upload files','F',NULL,0,NULL,NULL,'storage:file:write','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_menu_manage','0','Manage menus','F',NULL,0,NULL,NULL,'system:menu:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_menu_view','0','View menus','F',NULL,0,NULL,NULL,'system:menu:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_org_manage','0','Manage organizations','F',NULL,0,NULL,NULL,'system:organization:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_org_view','0','View organizations','F',NULL,0,NULL,NULL,'system:organization:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_process_manage','0','Manage process definitions','F',NULL,0,NULL,NULL,'process:definition:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_process_publish','0','Publish process definitions','F',NULL,0,NULL,NULL,'process:definition:publish','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_process_signal','0','Trigger process receive tasks','F',NULL,0,NULL,NULL,'process:instance:signal','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_process_view','0','View process definitions','F',NULL,0,NULL,NULL,'process:definition:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_role_manage','0','Manage roles','F',NULL,0,NULL,NULL,'system:role:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_role_view','0','View roles','F',NULL,0,NULL,NULL,'system:role:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_user_manage','0','Manage users','F',NULL,0,NULL,NULL,'system:user:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_user_reset','0','Reset user password','F',NULL,0,NULL,NULL,'system:user:reset-password','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('security_perm_user_view','0','View users','F',NULL,0,NULL,NULL,'system:user:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('sla_management_dir_001','400','SLA管理','M','Timer',8,'/system/sla',NULL,NULL,'0','0','0','1','SLA策略与运行监控',0,'migration-v075',CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_detail_perm_001','system_audit_menu_001','系统日志详情','F','',990,'','','system:audit:detail','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_export_perm_001','system_audit_menu_001','系统日志导出','F','',990,'','','system:audit:export','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_list_perm_001','system_audit_menu_001','系统日志查询','F','',990,'','','system:audit:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_menu_001','400','系统日志','C','Document',80,'/system/audit-logs','system/SystemAudit',NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('task_sla_monitor_menu_001','sla_management_dir_001','SLA监控','C','DataAnalysis',2,'/system/sla/monitor','process/TaskSlaMonitor','process:sla:monitor','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('task_sla_policy_manage_perm_001','task_sla_policy_menu_001','维护SLA策略','F',NULL,1,'','','process:sla-policy:manage','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('task_sla_policy_menu_001','sla_management_dir_001','SLA策略','C','Timer',1,'/system/sla/policies','process/TaskSlaPolicyManagement','process:sla-policy:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('task_sla_policy_publish_perm_001','task_sla_policy_menu_001','发布SLA策略','F',NULL,2,'','','process:sla-policy:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_dir_001','0','用户手册','M','Notebook',6,'/manual',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_embed_integration_001','user_manual_dir_001','嵌入集成','C','Monitor',4,'/manual/embed-integration','manual/EmbedIntegrationManual','user-manual:embed-integration:view','0','0','0','1','Embed View、应用授权、外部用户映射、Launch 与宿主 SDK 使用手册',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_entity_001','user_manual_dir_001','实体配置','C','Document',1,'/manual/entity','manual/EntityManual','user-manual:entity:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_open_integration_001','user_manual_dir_001','集成应用与 Embed','C','Link',3,'/manual/open-integration','manual/OpenIntegrationManual','user-manual:open-integration:view','0','0','0','1','集成应用、Client Credential、OAuth 与 Embed 接入说明',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_process_001','user_manual_dir_001','流程管理','C','Connection',2,'/manual/process','manual/ProcessManual','user-manual:process:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_quick_start_001','user_manual_dir_001','快速开始','C','Guide',0,'/manual/quick-start','manual/QuickStartManual','user-manual:quick-start:view','0','0','0','1','从配置流程、实体表单和列表到绑定流程、配置菜单的入门步骤',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('work_calendar_manage_perm_001','work_calendar_menu_001','维护工作日历','F',NULL,1,'','','system:work-calendar:manage','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('work_calendar_menu_001','400','工作日历','C','Calendar',7,'/system/work-calendars','system/WorkCalendarManagement','system:work-calendar:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`, `resource_type`, `list_key`) VALUES ('work_calendar_publish_perm_001','work_calendar_menu_001','发布工作日历','F',NULL,2,'','','system:work-calendar:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);

-- 全局职务定义表的系统初始记录。
INSERT INTO `sys_position` (`id`, `position_code`, `position_name`, `applicable_unit_type`, `holder_mode`, `built_in`, `status`, `sort_order`, `description`, `revision`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`) VALUES ('position_unit_leader_001','UNIT_LEADER','负责人','ANY','SINGLE',1,'ENABLED',10,'组织或部门负责人；旧 leader 字段的权威任职来源',1,'migration-v069','migration-v069',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);

-- 系统角色表的系统初始记录。
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `sort`, `status`, `create_time`, `update_time`, `deleted`, `sort_order`) VALUES ('1','已更新角色','super_admin','更新后的描述',0,'0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0);

-- 角色菜单权限关联表的系统初始记录。
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('00e39edf5c50bf3c7a691e3abd740378','1','list_column_template_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('03289d58b1fc7178ff46d6dff1476e9d','1','position_assign_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('0738c3d0253bc328872fbb37bf678ea2','1','global_settings_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('08122bbb3eea6a2141bc5b2c0a0a65f7','1','work_calendar_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('09d1a148efc935e59f85cff8e0077917','1','security_perm_menu_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('0a446aa774fce4e90e322621695b54ab','1','external_system_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('0fd6e9fdba7cdf617d1f50011543ca8e','1','position_manage_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('1354fb8ab53c1ed349577b7ba14226e9','1','security_perm_role_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('149312798634ce6f7a0f1bae0b45ebe9','1','security_perm_entity_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('18a946f6332387169ab762dbc3720556','1','security_perm_user_reset',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('1dec8f0ec49c31c224d02337a721ca9b','1','security_perm_org_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054120787970','1','300',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054129176579','1','400',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054141759490','1','403',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054158536706','1','config_migration_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054162731010','1','config_migration_list_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054166925314','1','config_migration_export_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054171119618','1','config_migration_download_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054175313922','1','config_migration_import_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054179508226','1','config_migration_analyze_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054183702529','1','config_migration_publish_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054187896834','1','config_migration_rollback_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054192091137','1','dev_guide_dir',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054192091138','1','dev_guide_list',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054196285441','1','flow_setting_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054196285442','1','flow_action_guide_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054200479745','1','custom_list_guide',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054208868353','1','custom_form_guide',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054213062657','1','user_manual_dir_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054217256961','1','user_manual_entity_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2080586054221451265','1','user_manual_process_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2645fafb4cb2d4ab1718a522021b88ee','1','embed_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('265953de88d211f1b9e8f79d2d09a523','1','extension_list_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('265955b488d211f1b9e8f79d2d09a523','1','extension_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2659562288d211f1b9e8f79d2d09a523','1','extension_test_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2659568688d211f1b9e8f79d2d09a523','1','extension_update_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2b58ccf45f7952af77d9c628903ea6d3','1','sla_management_dir_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2babeffb7ff59b9f58563d683709f417','1','security_perm_user_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('2fb4ac5920a5efb0e15a9243b154eb1a','1','security_perm_dict_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('30c5b440794425d37f8b4fd1505d5c13','1','assignee_incident_handle_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('328867562617bb929d1481789903f792','1','external_system_view_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('34e3f2279ce59857ed96eab6ca8917aa','1','security_perm_entity_publish',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('4876190b55d2da7be050d12676b793bda59ae3e9a68300f3ef858f3e91e1b120','1','entity_version_record_view_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('48ae6519ae82ea9129f204ec89ea3377','1','global_settings_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('4e62eabc457af7dcdc56ca45c430e4d6','1','integration_perm_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('4fdaa562a5bc238a088ebdd1d789154d','1','security_perm_process_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('50420884b78cfa827cc3ab9f38e0512c','1','task_sla_policy_publish_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('5247a13fc8e62351a49d983553d2f7f5','1','work_calendar_manage_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('525610baf86ec1e3adeac8a92aba7a8d','1','security_perm_process_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('546a07e11c06c471218e654589401463','1','position_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('55feae72f9d8990c5baad24327202c9b66f354c061bb8fa12b9ce73919d7ea60','1','entity_version_record_capture_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('5db73118888f11f1a02e52aa5ed9252f','1','system_audit_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('5fd0a378842fbd966c85b20284dc95b9','1','security_perm_role_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('60c5b627c72a07be37c86100c55c5ffd','1','integration_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('629d8bdc88f561028367b9db30a06f24','1','entity_version_management_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('6a9d1a1a3a1f1690896b5d6afc27dd09','1','work_calendar_publish_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('6f72a8c786ff1f30d5a03784b1db7342','1','user_manual_embed_integration_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('73437b56888811f1a02e52aa5ed9252f','1','system_audit_detail_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('73438326888811f1a02e52aa5ed9252f','1','system_audit_export_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('73438452888811f1a02e52aa5ed9252f','1','system_audit_list_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('77274acfbca78ed28e32315ff1e8bfe9','1','global_settings_menu',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('774ad263cdffd3a42e880733db29d618','1','security_perm_user_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('77a93d3f2f55b8d29cb9787ef3dee1e5','1','entity_scope_explicit_all_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('77ca7cb65b7f2138c812aa04bea1000b','1','integration_perm_secret_rotate',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('7a932ae42063ffa3a0568ca6b02fa3b1','1','assignee_incident_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('7f2db854d19c52af67b69b54ff08d5e7','1','list_column_template_manage_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('80b73a9fa2b2a876bfe18a0e0fa92570','1','security_perm_process_signal',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('83762738c6a8df0fc9f6265ecf170827','1','security_perm_entity_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('848bb511a33540d3afd1a32290a09cac','1','external_system_manage_permission_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('85de4afcadd2f28ed310d60560c5f833','1','task_sla_monitor_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('891042c416d95c26ec89827cfc2cb595','1','security_perm_process_publish',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('92606bb7c82d2688538c3d9aef521190','1','entity_version_config_update_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('9965553b538c05de8ed97b0f8a505711','1','security_perm_file_delete',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('a3759a57d5a664580a13c93dd46a4dc2','1','security_perm_dict_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('a7f105a87027044cb40d5d2286e2a529','1','task_sla_policy_menu_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('ad752fac598351a4b7aff641a592c0c9','1','embed_perm_publish',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('b0d591c126e47108bfe48b6cce23f906','1','integration_perm_view',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('b3e0385475f59111486b8ee903d983e7','1','embed_perm_session_revoke',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('bfc91a13fe9e230578fdb666565e32f9','1','task_sla_policy_manage_perm_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('c4e997351779bfbfe41190a34cb64a59','1','embed_perm_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('c7efe78a77b873a80d7695bdaa622de4','1','security_perm_menu_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('d8585f0e7fb4b0e1d5b65c17ec0d5a8d','1','entity_version_config_list_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('d8c7fae434846be9955701bca8e8b82c','1','security_perm_file_read',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('dec00d4a36f9fc8cc09c13eee43bf5dc','1','user_manual_quick_start_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('e079416319a588aeec38398f930c1ce5','1','user_manual_open_integration_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('f2ddc5d96a9b707f7f7987bbe00a7b24','1','entity_version_config_publish_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('f4df0f29d3b790164aec97b3fd36d645','1','list_field_guide_v2_001',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('f6f6487da585d22f311fb7cab03006ea','1','security_perm_file_write',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('f7502878d9a1bf8273184abc648a8e14','1','security_perm_org_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('fa85456c4c2efdd7e4e03b9cddce27a7','1','embed_perm_identity_manage',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('fca60e3e87e011f1a02e52aa5ed9252f','1','entity_ui_hotfix_publish_permission',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('fca636d487e011f1a02e52aa5ed9252f','1','entity_ui_hotfix_override_permission',CURRENT_TIMESTAMP);
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES ('fffc1112adfbecab7f3f747beab1ad10','1','embed_perm_view',CURRENT_TIMESTAMP);

-- 用户角色关联表的系统初始记录。
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_time`) VALUES ('2029842904071012353','1','1',CURRENT_TIMESTAMP);

-- 为新库随机生成迁移包签名密钥，避免复用现有库的密钥。
INSERT INTO `sys_global_setting` (`id`, `scope_type`, `owner_id`, `setting_key`, `name`, `setting_value_type`, `setting_value`, `remark`) VALUES ('setting_migration_signing_key', 'SYSTEM', '0', 'config.migration.signing_key', '配置迁移签名密钥', 'STRING', JSON_QUOTE(LOWER(HEX(RANDOM_BYTES(32)))), '用于迁移包 HMAC-SHA256 签名与验签；每个新库独立生成，不应跨库复用。');

-- 只初始化超级管理员；历史公开占位哈希仅用于 Bootstrap 识别，账号初始禁用。
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `password`, `email`, `phone`, `avatar`, `status`, `create_time`, `update_time`, `deleted`, `org_id`, `dept_id`, `password_reset_required`, `token_version`) VALUES ('1', 'admin', '超级管理员', '$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6', 'admin@workflow.com', NULL, NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL, 1, 0);

SET FOREIGN_KEY_CHECKS=@INIT_OLD_FOREIGN_KEY_CHECKS;
