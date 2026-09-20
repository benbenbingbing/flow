-- 完整退役应用级发布候选，仅清理候选编排数据；V053 等历史迁移保持不变。
-- 执行前须停止旧版本候选接口并排空在途发布；需要留档的候选报告应提前备份。
-- 不撤销已经发布的配置，也不删除配置迁移批次、快照、基线、环境映射或操作审计。
-- 普通导入、导出、发布和批次回滚继续通过 config-migration 权限与原有服务运行。

-- 按固定 ID、权限、页面路径和组件识别历史菜单及其按钮，兼容复制过的菜单。
-- 先删除授权，同时处理菜单已被人工删除后遗留的固定 ID 孤儿授权。
DELETE role_grant
FROM sys_role_menu role_grant
LEFT JOIN sys_menu menu ON menu.id = role_grant.menu_id
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id
WHERE role_grant.menu_id IN (
          'release_candidate_menu_001',
          'release_candidate_create_001',
          'release_candidate_publish_001',
          'release_candidate_recover_001'
      )
   OR menu.perm IN (
          'release-candidate:list', 'release-candidate:create',
          'release-candidate:publish', 'release-candidate:recover'
      )
   OR menu.path = '/system/release-candidates'
   OR menu.component = 'system/ReleaseCandidateManagement'
   OR parent.id = 'release_candidate_menu_001'
   OR parent.perm = 'release-candidate:list'
   OR parent.path = '/system/release-candidates'
   OR parent.component = 'system/ReleaseCandidateManagement';

DELETE menu
FROM sys_menu menu
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id
WHERE menu.id IN (
          'release_candidate_menu_001',
          'release_candidate_create_001',
          'release_candidate_publish_001',
          'release_candidate_recover_001'
      )
   OR menu.perm IN (
          'release-candidate:list', 'release-candidate:create',
          'release-candidate:publish', 'release-candidate:recover'
      )
   OR menu.path = '/system/release-candidates'
   OR menu.component = 'system/ReleaseCandidateManagement'
   OR parent.id = 'release_candidate_menu_001'
   OR parent.perm = 'release-candidate:list'
   OR parent.path = '/system/release-candidates'
   OR parent.component = 'system/ReleaseCandidateManagement';

-- 仅删除六张候选专属表，先子表后主表；普通发布和回滚不依赖这些表。
DROP TABLE IF EXISTS `release_candidate_report`;
DROP TABLE IF EXISTS `release_candidate_step`;
DROP TABLE IF EXISTS `release_candidate_validation`;
DROP TABLE IF EXISTS `release_candidate_dependency`;
DROP TABLE IF EXISTS `release_candidate_item`;
DROP TABLE IF EXISTS `release_candidate`;
