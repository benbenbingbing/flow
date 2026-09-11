-- 完整退役“实体变更策略”。V044 及更早的建表迁移已进入主分支，必须通过
-- 新的前向迁移清理专属菜单、角色授权和持久化结构。
--
-- entity_change_target_instance 属于曾经的审批后跨实体变更链路；本 contract
-- 迁移按退场目标删除其中全部状态的数据，执行前必须完成旧实例与在途事务排空。
-- entity_mutation_receipt 是实体写入与界面动作共享的幂等底座，明确保留。

-- V080 为兼容审计保留了旧数据版本发布原文，V082 还可能把 active release
-- 投影为当前配置。只移除其中属于变更策略的执行步骤与跨实体目标；scenarios 和
-- triggers 仍用于数据版本匹配，数据版本配置、发布历史及记录版本本体均保留。
UPDATE `entity_version_config`
SET `config_document` = JSON_REMOVE(
        CAST(`config_document` AS JSON),
        '$.steps', '$.targetBindings')
WHERE JSON_VALID(`config_document`) = 1;

UPDATE `entity_version_config`
SET `draft_document` = JSON_REMOVE(
        CAST(`draft_document` AS JSON),
        '$.steps', '$.targetBindings')
WHERE JSON_VALID(`draft_document`) = 1;

UPDATE `entity_version_config_release`
SET `config_document` = JSON_REMOVE(
        CAST(`config_document` AS JSON),
        '$.steps', '$.targetBindings')
WHERE JSON_VALID(`config_document`) = 1;

-- 不能只依赖历史固定 ID：存量环境可能复制过页面菜单或重新生成过主键。
-- 删除授权和菜单时重复使用同一组功能特征，避免为一次性迁移要求
-- CREATE TEMPORARY TABLES 这项额外数据库权限。
-- 先删授权，包含菜单已被人工删除后遗留的固定 ID 孤儿授权。
DELETE role_grant
FROM sys_role_menu role_grant
LEFT JOIN sys_menu menu ON menu.id = role_grant.menu_id
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id
WHERE role_grant.menu_id IN (
          'entity_mutation_policy_management_001',
          'entity_mutation_policy_list_001',
          'entity_mutation_policy_update_001',
          'entity_mutation_policy_publish_001'
      )
   OR menu.perm IN (
          'entity:mutation:config:list',
          'entity:mutation:config:update',
          'entity:mutation:config:publish'
      )
   OR menu.path = '/system/entity-mutation-policies'
   OR menu.component = 'system/EntityMutationPolicyManagement'
   OR parent.id = 'entity_mutation_policy_management_001'
   OR parent.perm = 'entity:mutation:config:list'
   OR parent.path = '/system/entity-mutation-policies'
   OR parent.component = 'system/EntityMutationPolicyManagement';

DELETE menu
FROM sys_menu menu
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id
WHERE menu.id IN (
          'entity_mutation_policy_management_001',
          'entity_mutation_policy_list_001',
          'entity_mutation_policy_update_001',
          'entity_mutation_policy_publish_001'
      )
   OR menu.perm IN (
          'entity:mutation:config:list',
          'entity:mutation:config:update',
          'entity:mutation:config:publish'
      )
   OR menu.path = '/system/entity-mutation-policies'
   OR menu.component = 'system/EntityMutationPolicyManagement'
   OR parent.id = 'entity_mutation_policy_management_001'
   OR parent.perm = 'entity:mutation:config:list'
   OR parent.path = '/system/entity-mutation-policies'
   OR parent.component = 'system/EntityMutationPolicyManagement';

DROP TABLE IF EXISTS `entity_change_target_instance`;
DROP TABLE IF EXISTS `entity_mutation_policy_release`;
DROP TABLE IF EXISTS `entity_mutation_policy_config`;
