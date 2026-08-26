-- 完整退役配置测试中心、配置资产智能和平台增强中心。
-- V056、V057、V058、V059 已经进入主分支，必须通过新的前向迁移撤除，不能修改历史迁移。

-- 先按菜单真实属性清理角色授权，兼容菜单 ID 被复制、重建或调整过的数据库。
DELETE role_grant
FROM sys_role_menu role_grant
JOIN sys_menu menu ON menu.id = role_grant.menu_id
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id
WHERE menu.id IN (
          '2075600000000000001', '2075600000000000002',
          '2075600000000000003', '2075600000000000004',
          '2075700000000000001', '2075700000000000002',
          '2075700000000000003',
          '2075800000000000001', '2075800000000000002',
          '2075800000000000003', '2075800000000000004',
          '2075800000000000005', '2075800000000000006',
          '2075800000000000007',
          '2075900000000000001', '2075900000000000002',
          '2075900000000000003', '2075900000000000004',
          '2075900000000000005'
      )
   OR menu.perm IN (
          'config:test:list', 'config:test:manage', 'config:test:run', 'config:test:export',
          'config:intelligence:list', 'config:intelligence:manage', 'config:intelligence:analyze',
          'platform:capability:list',
          'index-advisor:analyze', 'index-advisor:execute',
          'config-reference:list',
          'config-collaboration:manage', 'config-collaboration:review',
          'config-collaboration:schedule',
          'process-instance-migration:preview', 'process-instance-migration:execute'
      )
   OR menu.path IN (
          '/system/config-test-center',
          '/system/config-intelligence',
          '/system/platform-capabilities'
      )
   OR menu.component IN (
          'system/ConfigTestCenter',
          'system/ConfigurationIntelligence',
          'system/PlatformCapabilityCenter'
      )
   OR parent.id IN (
          '2075600000000000001',
          '2075700000000000001',
          '2075800000000000001'
      )
   OR parent.perm IN (
          'config:test:list',
          'config:intelligence:list',
          'platform:capability:list'
      )
   OR parent.path IN (
          '/system/config-test-center',
          '/system/config-intelligence',
          '/system/platform-capabilities'
      )
   OR parent.component IN (
          'system/ConfigTestCenter',
          'system/ConfigurationIntelligence',
          'system/PlatformCapabilityCenter'
      );

-- 功能权限节点直接挂在页面菜单下，先删子节点再删页面节点。
DELETE child
FROM sys_menu child
JOIN sys_menu parent ON parent.id = child.parent_id
WHERE parent.id IN (
          '2075600000000000001',
          '2075700000000000001',
          '2075800000000000001'
      )
   OR parent.perm IN (
          'config:test:list',
          'config:intelligence:list',
          'platform:capability:list'
      )
   OR parent.path IN (
          '/system/config-test-center',
          '/system/config-intelligence',
          '/system/platform-capabilities'
      )
   OR parent.component IN (
          'system/ConfigTestCenter',
          'system/ConfigurationIntelligence',
          'system/PlatformCapabilityCenter'
      );

DELETE FROM sys_menu
WHERE id IN (
          '2075600000000000001', '2075600000000000002',
          '2075600000000000003', '2075600000000000004',
          '2075700000000000001', '2075700000000000002',
          '2075700000000000003',
          '2075800000000000001', '2075800000000000002',
          '2075800000000000003', '2075800000000000004',
          '2075800000000000005', '2075800000000000006',
          '2075800000000000007',
          '2075900000000000001', '2075900000000000002',
          '2075900000000000003', '2075900000000000004',
          '2075900000000000005'
      )
   OR perm IN (
          'config:test:list', 'config:test:manage', 'config:test:run', 'config:test:export',
          'config:intelligence:list', 'config:intelligence:manage', 'config:intelligence:analyze',
          'platform:capability:list',
          'index-advisor:analyze', 'index-advisor:execute',
          'config-reference:list',
          'config-collaboration:manage', 'config-collaboration:review',
          'config-collaboration:schedule',
          'process-instance-migration:preview', 'process-instance-migration:execute'
      )
   OR path IN (
          '/system/config-test-center',
          '/system/config-intelligence',
          '/system/platform-capabilities'
      )
   OR component IN (
          'system/ConfigTestCenter',
          'system/ConfigurationIntelligence',
          'system/PlatformCapabilityCenter'
      );

-- 索引顾问曾把执行审计写入实体结构操作共享表。只清除该来源的历史记录，
-- 保留实体发布仍在使用的共享表、来源字段以及已实际创建的数据库索引。
DELETE operation_event
FROM entity_schema_operation_event operation_event
JOIN entity_schema_operation schema_operation
  ON schema_operation.id = operation_event.operation_id
WHERE schema_operation.operation_source = 'INDEX_ADVISOR';

DELETE FROM entity_schema_operation
WHERE operation_source = 'INDEX_ADVISOR';

-- 配置测试中心专属数据。
DROP TABLE IF EXISTS config_test_result;
DROP TABLE IF EXISTS config_test_run;
DROP TABLE IF EXISTS config_test_case;
DROP TABLE IF EXISTS config_test_suite;

-- 配置资产智能专属数据；不要与核心配置迁移表 config_migration_asset_dependency 混淆。
DROP TABLE IF EXISTS config_quality_snapshot;
DROP TABLE IF EXISTS config_asset_dependency;
DROP TABLE IF EXISTS config_blueprint;

-- 平台增强中心的索引建议、配置协作/定时发布和流程实例迁移专属数据。
DROP TABLE IF EXISTS entity_index_advice;
DROP TABLE IF EXISTS config_scheduled_release;
DROP TABLE IF EXISTS config_collaboration_review;
DROP TABLE IF EXISTS config_collaboration_comment;
DROP TABLE IF EXISTS config_collaboration_branch;
DROP TABLE IF EXISTS config_collaboration_workspace;
DROP TABLE IF EXISTS process_instance_migration_audit;
DROP TABLE IF EXISTS process_instance_migration_lock;
DROP TABLE IF EXISTS process_instance_migration_item;
DROP TABLE IF EXISTS process_instance_migration_batch;

-- config_migration_asset_dependency、entity_schema_operation 及其事件表仍服务于核心功能，明确保留。
