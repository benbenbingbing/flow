# 数据库迁移说明

## 自动迁移（推荐）

Flyway 会在应用启动时自动执行迁移脚本。只需启动后端服务：

```bash
cd workflow-server
mvn spring-boot:run
```

Flyway 会自动检测并执行 `resources/db/migration/` 目录下的所有脚本。

---

## 手动迁移

如果 Flyway 自动执行失败，可以手动执行：

### 1. 检查当前表结构

```sql
-- 查看已存在的表
SHOW TABLES;

-- 查看 Flyway 迁移记录
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;
```

### 2. 手动执行合并脚本

```bash
# 使用 MySQL 命令行登录
mysql -u root -p

# 选择数据库
USE workflow_db;

# 执行合并脚本
SOURCE /path/to/workflow-server/src/main/resources/db/migration/V16__combined_migration_check.sql
```

或者使用 Navicat/DBeaver 等工具执行 `V16__combined_migration_check.sql` 文件。

---

## 迁移脚本列表

| 版本 | 文件名 | 说明 |
|-----|--------|------|
| V10_1 | V10_1__add_entity_code_to_menu.sql | 菜单表添加 entity_code |
| V11 | V11__process_center_tables.sql | 流程中心表（待办/意见/草稿/日志） |
| V12 | V12__view_engine_tables.sql | 视图引擎表 |
| V13 | V13__report_engine_tables.sql | 报表引擎表 |
| V14 | V14__workbench_tables.sql | 工作台表 |
| V15 | V15__service_orchestration_tables.sql | 服务编排表 |
| V16 | V16__combined_migration_check.sql | 合并检查脚本 |

---

## 验证迁移结果

执行以下 SQL 验证表是否创建成功：

```sql
-- 流程中心表
SELECT COUNT(*) FROM process_task_instance;
SELECT COUNT(*) FROM process_common_opinion;
SELECT COUNT(*) FROM process_draft;

-- 视图引擎表
SELECT COUNT(*) FROM view_definition;
SELECT COUNT(*) FROM view_field_config;

-- 报表引擎表
SELECT COUNT(*) FROM report_definition;
SELECT COUNT(*) FROM report_category;

-- 服务编排表
SELECT COUNT(*) FROM service_definition;
SELECT COUNT(*) FROM service_node;
SELECT COUNT(*) FROM service_execution_log;
```

如果都能正常查询（返回 0 条或空结果），说明表已创建成功。

---

## 常见问题

### 1. 表已存在错误

如果手动执行时提示表已存在，说明 Flyway 已经自动执行过了，无需重复执行。

### 2. Flyway 校验失败

如果修改了已执行的脚本，会导致 Flyway 校验失败。解决方法：

```sql
-- 删除 Flyway 记录（谨慎操作）
DELETE FROM flyway_schema_history WHERE version = '目标版本';
```

### 3. 字符集问题

确保数据库使用 utf8mb4 字符集：

```sql
ALTER DATABASE workflow_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
