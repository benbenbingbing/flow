-- biz-project 扩展示例：禁用模块时保留资料，不自动删除表。
CREATE TABLE biz_project_user_profile (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    technical_level VARCHAR(32) NULL,
    skill_direction VARCHAR(128) NULL,
    row_version BIGINT NOT NULL DEFAULT 0
) COMMENT='本地维护的研发补充资料';
CREATE TABLE biz_project_directory_user (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    employee_number VARCHAR(64) NULL,
    row_version BIGINT NOT NULL DEFAULT 0
) COMMENT='由 biz 主动写入的业务人员资料';
CREATE TABLE biz_project_org_profile (
    organization_id VARCHAR(64) NOT NULL PRIMARY KEY,
    delivery_code VARCHAR(64) NULL,
    row_version BIGINT NOT NULL DEFAULT 0
) COMMENT='本地维护的项目组织资料';
