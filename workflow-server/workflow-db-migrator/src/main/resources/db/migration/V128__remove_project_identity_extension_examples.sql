-- V127 已执行，保留原迁移以维持 Flyway 历史校验；通过新版本清理产品不再预置的示例表。
-- 仅删除 V127 创建的这三张表（包括表内数据），业务需要的扩展表由业务自行定义和建表。
DROP TABLE IF EXISTS biz_project_user_profile;
DROP TABLE IF EXISTS biz_project_directory_user;
DROP TABLE IF EXISTS biz_project_org_profile;
