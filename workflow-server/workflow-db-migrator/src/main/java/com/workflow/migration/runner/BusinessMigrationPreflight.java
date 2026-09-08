package com.workflow.migration.runner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates data conditions that would otherwise make a non-transactional
 * MySQL Flyway migration fail after partially applying DDL.
 */
public final class BusinessMigrationPreflight {

    private static final int SAMPLE_LIMIT = 10;

    private BusinessMigrationPreflight() {
    }

    public static void verify(Connection connection) throws SQLException {
        if (tableExists(connection, "entity_relation")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `parent_entity_id`, `relation_code`, COUNT(*)
                    FROM `entity_relation`
                    GROUP BY `parent_entity_id`, `relation_code`
                    HAVING COUNT(*) > 1
                    LIMIT 10
                    """,
                    2,
                    "V043 不能建立关系编码唯一约束",
                    "请先为同一父实体下重复的 relation_code 制定人工重命名映射");
            requireNoConflicts(
                    connection,
                    """
                    SELECT `parent_entity_id`,
                           COALESCE(NULLIF(TRIM(`parent_field_code`), ''),
                                    `relation_code`) AS effective_data_key,
                           COUNT(*)
                    FROM `entity_relation`
                    GROUP BY `parent_entity_id`, effective_data_key
                    HAVING COUNT(*) > 1
                    LIMIT 10
                    """,
                    2,
                    "V043 不能建立关系数据键唯一约束",
                    "请先为同一父实体下重复的有效 data_key 制定人工重命名映射");
        }
        if (tableExists(connection, "entity_record_version")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `entity_code`, `record_id`, `idempotency_key`,
                           COUNT(*)
                    FROM `entity_record_version`
                    GROUP BY `entity_code`, `record_id`, `idempotency_key`
                    HAVING COUNT(*) > 1
                    LIMIT 10
                    """,
                    3,
                    "V046 不能收紧业务数据版本幂等约束",
                    "请先逐条核对重复 Idempotency-Key 对应的数据版本，禁止静默删除审计记录");
        }
        if (!migrationApplied(connection, "069")
                && tableExists(connection, "sys_organization")
                && tableExists(connection, "sys_user")) {
            verifyPositionMigrationPrerequisites(connection);
        }
        if (!migrationApplied(connection, "070")
                && tableExists(connection, "sys_menu")
                && tableExists(
                    connection, "process_person_resolver_definition")) {
            verifyPositionMenuMigrationPrerequisites(connection);
        }
        if (!migrationApplied(connection, "072")
                && tableExists(
                connection, "process_person_resolver_definition")) {
            verifyEntityUserReferenceResolverPrerequisites(connection);
        }
        if (!migrationApplied(connection, "073")
                && tableExists(connection, "entity_definition")) {
            verifyEntityWorkflowBindingIndexPrerequisites(connection);
        }
        if (!migrationApplied(connection, "075")
                && tableExists(connection, "sys_menu")) {
            verifyNavigationMenuMigrationPrerequisites(connection);
        }
        if (migrationApplied(connection, "075")
                && !migrationApplied(connection, "076")
                && tableExists(connection, "sys_menu")) {
            verifyNavigationMenuRouteMigrationPrerequisites(connection);
        }
        if (!migrationApplied(connection, "079")) {
            verifyExternalSystemManagementMigrationPrerequisites(connection);
        }
    }

    /** 在 V072 seed 前拒绝固定 ID 或 resolver_code 被其他语义占用。 */
    private static void verifyEntityUserReferenceResolverPrerequisites(
            Connection connection) throws SQLException {
        requireNoConflicts(
                connection,
                """
                SELECT `id`, `resolver_code`, COUNT(*)
                FROM `process_person_resolver_definition`
                WHERE `id` = 'person_resolver_entity_user_reference_001'
                   OR `resolver_code` = 'entityUserReferenceField'
                GROUP BY `id`, `resolver_code`
                LIMIT 10
                """,
                2,
                "V072 实体用户关系字段解析器固定 ID 或编码已被占用",
                "请先核对解析器目录和 Flyway 历史，禁止覆盖现有解析器定义");
    }

    /** 在 V073 建 canonical 普通索引前拒绝无法安全解释的历史绑定。 */
    private static void verifyEntityWorkflowBindingIndexPrerequisites(
            Connection connection) throws SQLException {
        List<String> partialArtifacts = new ArrayList<>();
        if (columnExists(
                connection,
                "entity_definition",
                "active_process_definition_key")) {
            partialArtifacts.add(
                    "entity_definition.active_process_definition_key");
        }
        if (indexExists(
                connection,
                "entity_definition",
                "idx_entity_definition_process_binding")) {
            partialArtifacts.add(
                    "entity_definition.idx_entity_definition_process_binding");
        }
        failIfSamples(
                partialArtifacts,
                "V073 检测到目标字段或索引已存在的异常迁移状态",
                "请先核对数据库与 Flyway 历史并制定显式恢复方案");
        requireNoConflicts(
                connection,
                """
                SELECT entity_definition.`id`,
                       entity_definition.`process_definition_id`, 1
                FROM `entity_definition` entity_definition
                WHERE COALESCE(entity_definition.`deleted`, 0) = 0
                  AND entity_definition.`process_definition_id` IS NOT NULL
                  AND TRIM(entity_definition.`process_definition_id`) <> ''
                  AND (
                    TRIM(entity_definition.`process_definition_id`)
                      NOT REGEXP '^[0-9]+$'
                    OR NULLIF(TRIM(LEADING '0' FROM
                         TRIM(entity_definition.`process_definition_id`)), '')
                         IS NULL
                    OR CHAR_LENGTH(TRIM(LEADING '0' FROM
                         TRIM(entity_definition.`process_definition_id`))) > 19
                    OR (
                      CHAR_LENGTH(TRIM(LEADING '0' FROM
                        TRIM(entity_definition.`process_definition_id`))) = 19
                      AND TRIM(LEADING '0' FROM
                        TRIM(entity_definition.`process_definition_id`))
                          > '9223372036854775807'
                    )
                  )
                LIMIT 10
                """,
                2,
                "V073 检测到不能规范为正 BIGINT 的流程绑定",
                "请先修复非法 process_definition_id，禁止静默改写损坏值");
        if (!tableExists(connection, "process_definition_config")) {
            throw new IllegalStateException(
                    "V073 缺少 process_definition_config，不能验证实体流程绑定");
        }
        // 上一项已保证绑定值可安全转换为正 BIGINT。这里按数值关联，既保留
        // 01/1 的别名语义，也避免 CAST AS CHAR 继承连接排序规则后与列排序规则冲突。
        requireNoConflicts(
                connection,
                """
                SELECT entity_definition.`id`,
                       entity_definition.`process_definition_id`, 1
                FROM `entity_definition` entity_definition
                LEFT JOIN `process_definition_config` process_config
                  ON process_config.`id` = CAST(
                       TRIM(entity_definition.`process_definition_id`)
                       AS UNSIGNED)
                 AND COALESCE(process_config.`deleted`, 0) = 0
                WHERE COALESCE(entity_definition.`deleted`, 0) = 0
                  AND entity_definition.`process_definition_id` IS NOT NULL
                  AND TRIM(entity_definition.`process_definition_id`) <> ''
                  AND process_config.`id` IS NULL
                LIMIT 10
                """,
                2,
                "V073 检测到不存在流程定义的活动实体绑定",
                "请先恢复对应流程定义或解除经确认无效的实体绑定");
        requireNoConflicts(
                connection,
                """
                SELECT process_config.`id`, COUNT(*)
                FROM `entity_definition` entity_definition
                JOIN `process_definition_config` process_config
                  ON process_config.`id` = CAST(
                       TRIM(entity_definition.`process_definition_id`)
                       AS UNSIGNED)
                 AND COALESCE(process_config.`deleted`, 0) = 0
                WHERE COALESCE(entity_definition.`deleted`, 0) = 0
                  AND entity_definition.`process_definition_id` IS NOT NULL
                  AND TRIM(entity_definition.`process_definition_id`) <> ''
                GROUP BY process_config.`id`
                HAVING COUNT(*) > 1
                LIMIT 10
                """,
                1,
                "V073 检测到一个流程绑定多个活动实体",
                "请先人工确认并保留唯一实体绑定；本次 expand 不自动猜测保留项");
    }

    /**
     * 在 V069 执行任何 MySQL DDL 前验证负责人、用户归属与组织父链。
     *
     * <p>MySQL DDL 不随整个迁移事务回滚，因此这些检查必须在
     * {@code Flyway.migrate()} 前完成，不能等外键创建或负责人回填时才失败。</p>
     */
    private static void verifyPositionMigrationPrerequisites(
            Connection connection) throws SQLException {
        requireNoConflicts(
                connection,
                """
                SELECT organization_unit.`id`, organization_unit.`leader_id`, COUNT(*)
                FROM `sys_organization` organization_unit
                LEFT JOIN `sys_user` leader
                  ON leader.`id` = organization_unit.`leader_id`
                 AND leader.`deleted` = 0
                 AND leader.`status` = '0'
                WHERE organization_unit.`deleted` = 0
                  AND organization_unit.`leader_id` IS NOT NULL
                  AND TRIM(organization_unit.`leader_id`) <> ''
                  AND leader.`id` IS NULL
                GROUP BY organization_unit.`id`, organization_unit.`leader_id`
                LIMIT 10
                """,
                2,
                "V069 不能回填悬空或已禁用的组织负责人",
                "请先启用负责人，或修复/清空样例组织的 leader_id，禁止静默遗漏负责人任职");
        verifyOrganizationAndUserHierarchy(connection);
        if (tableExists(connection, "sys_dict")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `dict_code`, `id`, COUNT(*)
                    FROM `sys_dict`
                    WHERE `dict_code` = 'organization_business_level'
                      AND `deleted` = 0
                    GROUP BY `dict_code`, `id`
                    LIMIT 10
                    """,
                    2,
                    "V069 预留的组织业务层级字典编码已被占用",
                    "请先确认现有字典语义并制定显式合并方案");
            requireNoConflicts(
                    connection,
                    """
                    SELECT `id`, `dict_code`, COUNT(*)
                    FROM `sys_dict`
                    WHERE `id` = 'dict_org_business_level_001'
                    GROUP BY `id`, `dict_code`
                    LIMIT 10
                    """,
                    2,
                    "V069 组织业务层级字典固定 ID 已被占用",
                    "请先迁移占用该 ID 的其他语义数据，避免 DDL 后 seed 插入失败");
        }
        if (tableExists(connection, "sys_dict_item")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `id`, CONCAT(`dict_code`, ':', `item_code`), COUNT(*)
                    FROM `sys_dict_item`
                    WHERE `dict_code` = 'organization_business_level'
                      AND `deleted` = 0
                    GROUP BY `id`, `dict_code`, `item_code`
                    LIMIT 10
                    """,
                    2,
                    "V069 预留的组织业务层级代码项语义已被占用",
                    "请先确认现有代码项并制定显式合并方案，禁止产生重复业务层级编码");
            requireNoConflicts(
                    connection,
                    """
                    SELECT `id`, CONCAT(`dict_code`, ':', `item_code`), COUNT(*)
                    FROM `sys_dict_item`
                    WHERE `id` IN (
                      'dict_org_level_group_001',
                      'dict_org_level_company_001',
                      'dict_org_level_center_001',
                      'dict_org_level_dept1_001',
                      'dict_org_level_dept2_001',
                      'dict_org_level_team_001'
                    )
                    GROUP BY `id`, `dict_code`, `item_code`
                    LIMIT 10
                    """,
                    2,
                    "V069 组织业务层级代码项固定 ID 已被占用",
                    "请先迁移占用固定 ID 的其他语义数据，避免 DDL 后 seed 插入失败");
        }
        if (tableExists(connection, "sys_position")
                && columnExists(connection, "sys_position", "id")
                && columnExists(
                    connection, "sys_position", "position_code")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `position_code`, `id`, COUNT(*)
                    FROM `sys_position`
                    WHERE `position_code` = 'UNIT_LEADER'
                       OR `id` = 'position_unit_leader_001'
                    GROUP BY `position_code`, `id`
                    LIMIT 10
                    """,
                    2,
                    "V069 预留的 UNIT_LEADER 职务编码已被占用",
                    "请先核对部分迁移状态，禁止覆盖或复用已有职务编码");
        }
        verifyNoPartialPositionSchema(connection);
    }

    /**
     * V069 包含不可事务回滚的 ALTER/CREATE，任何目标对象已存在都表示
     * 人工建表或失败迁移残留；必须先清点恢复，不能让 Flyway 继续执行。
     */
    private static void verifyNoPartialPositionSchema(
            Connection connection) throws SQLException {
        List<String> artifacts = new ArrayList<>();
        if (columnExists(
                connection, "sys_organization", "business_level_code")) {
            artifacts.add("sys_organization.business_level_code");
        }
        for (String table : List.of(
                "sys_position",
                "sys_position_assignment",
                "sys_position_assignment_batch")) {
            if (tableExists(connection, table)) {
                artifacts.add(table);
            }
        }
        failIfSamples(
                artifacts,
                "V069 检测到目标字段或表已存在的部分迁移状态",
                "请先核对数据库与 Flyway 历史并制定显式恢复方案，禁止在非事务 DDL 上续跑");
    }

    /**
     * 在 V069 的 DDL 以及 V070 的目录写入前一次性验证全部固定资源。
     * 正确的历史系统管理父目录可以复用，但迁移绝不覆盖其内容。
     */
    private static void verifyPositionMenuMigrationPrerequisites(
            Connection connection) throws SQLException {
        requireNoConflicts(
                connection,
                """
                SELECT `id`,
                       CONCAT_WS(':', `parent_id`, `menu_name`, `menu_type`,
                                 COALESCE(`path`, ''), `status`, `visible`,
                                 `deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu`
                WHERE `id` = '400'
                  AND NOT (
                    COALESCE(`parent_id`, '') = '0'
                    AND COALESCE(`menu_name`, '') = '系统管理'
                    AND COALESCE(`menu_type`, '') = 'M'
                    AND COALESCE(`path`, '') IN ('/system', 'system')
                    AND COALESCE(TRIM(`perm`), '') = ''
                    AND COALESCE(`status`, '') = '0'
                    AND COALESCE(`visible`, '') = '0'
                    AND COALESCE(`deleted`, -1) = 0
                  )
                GROUP BY `id`, `parent_id`, `menu_name`, `menu_type`, `path`,
                         `status`, `visible`, `deleted`
                LIMIT 10
                """,
                2,
                "V070 既有系统管理父目录 400 语义错误或已停用",
                "请先恢复为启用的系统管理目录；迁移不会覆盖既有菜单内容");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, COALESCE(`menu_name`, ''), COUNT(*)
                FROM `sys_menu`
                WHERE `id` IN (
                  'position_management_menu_001',
                  'position_manage_permission_001',
                  'position_assign_permission_001'
                )
                GROUP BY `id`, `menu_name`
                LIMIT 10
                """,
                2,
                "V070 职务管理菜单固定 ID 已被占用或存在部分迁移",
                "请先迁移冲突资源或核对 Flyway 历史，禁止覆盖既有菜单");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, `path`, COUNT(*)
                FROM `sys_menu`
                WHERE `path` = '/system/position'
                GROUP BY `id`, `path`
                LIMIT 10
                """,
                2,
                "V070 职务管理菜单路径已被占用",
                "请先调整冲突路由，禁止生成两个相同的系统管理页面入口");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, `perm`, COUNT(*)
                FROM `sys_menu`
                WHERE `perm` IN (
                  'system:position:view',
                  'system:position:manage',
                  'system:position:assign'
                )
                GROUP BY `id`, `perm`
                LIMIT 10
                """,
                2,
                "V070 职务管理权限标识已被占用",
                "请先核对冲突权限语义，禁止把角色权限与职务身份混用");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, `resolver_code`, COUNT(*)
                FROM `process_person_resolver_definition`
                WHERE `id` = 'person_resolver_relative_position_001'
                   OR `resolver_code` = 'relativeOrgPosition'
                GROUP BY `id`, `resolver_code`
                LIMIT 10
                """,
                2,
                "V070 相对组织职务解析器固定 ID 或编码已被占用",
                "请先核对解析器目录和 Flyway 历史，禁止覆盖现有解析器定义");
    }

    /**
     * 在 V075 重组导航前校验稳定父目录和菜单 ID，避免把页面挂到被其他
     * 业务占用或已停用的目录，并提前检查紧随其后的 V076 目标路由冲突。
     */
    private static void verifyNavigationMenuMigrationPrerequisites(
            Connection connection) throws SQLException {
        requireNoConflicts(
                connection,
                """
                SELECT `id`,
                       CONCAT_WS(':', `parent_id`, `menu_name`, `menu_type`,
                                 `status`, `visible`, `deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu`
                WHERE `id` = '300'
                  AND NOT (
                    COALESCE(`parent_id`, '') = '0'
                    AND COALESCE(`menu_name`, '') = '配置管理'
                    AND COALESCE(`menu_type`, '') = 'M'
                    AND COALESCE(`path`, '') IN ('/entity', '/config')
                    AND COALESCE(TRIM(`perm`), '') = ''
                    AND COALESCE(`status`, '') = '0'
                    AND COALESCE(`visible`, '') = '0'
                    AND COALESCE(`deleted`, -1) = 0
                  )
                GROUP BY `id`, `parent_id`, `menu_name`, `menu_type`, `path`,
                         `status`, `visible`, `deleted`
                LIMIT 10
                """,
                2,
                "V075 既有配置管理父目录 300 语义错误或已停用",
                "请先恢复为启用的配置管理目录；迁移不会覆盖既有菜单内容");
        requireNoConflicts(
                connection,
                """
                SELECT `id`,
                       CONCAT_WS(':', `parent_id`, `menu_name`, `menu_type`,
                                 `status`, `visible`, `deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu`
                WHERE `id` = '400'
                  AND NOT (
                    COALESCE(`parent_id`, '') = '0'
                    AND COALESCE(`menu_name`, '') = '系统管理'
                    AND COALESCE(`menu_type`, '') = 'M'
                    AND COALESCE(TRIM(`perm`), '') = ''
                    AND COALESCE(`status`, '') = '0'
                    AND COALESCE(`visible`, '') = '0'
                    AND COALESCE(`deleted`, -1) = 0
                  )
                GROUP BY `id`, `parent_id`, `menu_name`, `menu_type`,
                         `status`, `visible`, `deleted`
                LIMIT 10
                """,
                2,
                "V075 既有系统管理父目录 400 语义错误或已停用",
                "请先恢复为启用的系统管理目录；迁移不会覆盖既有菜单内容");
        requireNoConflicts(
                connection,
                """
                SELECT `id`,
                       CONCAT_WS(':', `parent_id`, `menu_name`, `menu_type`,
                                 COALESCE(`path`, ''), `status`, `visible`,
                                 `deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu`
                WHERE (
                    `id` = 'dev_guide_dir'
                    AND NOT (
                      COALESCE(`parent_id`, '') = '0'
                      AND COALESCE(`menu_name`, '') = '定制开发'
                      AND COALESCE(`menu_type`, '') = 'M'
                      AND COALESCE(`path`, '') = '/dev'
                      AND COALESCE(`status`, '') = '0'
                      AND COALESCE(`visible`, '') = '0'
                      AND COALESCE(`deleted`, -1) = 0
                    )
                  ) OR (
                    `id` = 'flow_setting_menu_001'
                    AND NOT (
                      COALESCE(`parent_id`, '') = 'dev_guide_dir'
                      AND COALESCE(`menu_name`, '') IN ('流程配置', '开发手册')
                      AND COALESCE(`menu_type`, '') = 'M'
                      AND COALESCE(`path`, '') IN ('', '/dev/manual')
                      AND COALESCE(`status`, '') = '0'
                      AND COALESCE(`visible`, '') = '0'
                      AND COALESCE(`deleted`, -1) = 0
                    )
                  )
                GROUP BY `id`, `parent_id`, `menu_name`, `menu_type`, `path`,
                         `status`, `visible`, `deleted`
                LIMIT 10
                """,
                2,
                "V075 定制开发或开发手册目录语义错误",
                "请先恢复启用的定制开发/流程配置目录，禁止将指南迁入冲突资源");
        requireNoConflicts(
                connection,
                """
                SELECT expected.`id`, 'missing', 1
                FROM (
                  SELECT 'dev_guide_dir' AS `id`
                  UNION ALL SELECT 'flow_setting_menu_001'
                ) expected
                LEFT JOIN `sys_menu` menu_record
                  ON menu_record.`id` = expected.`id`
                WHERE menu_record.`id` IS NULL
                LIMIT 10
                """,
                2,
                "V075 缺少定制开发稳定目录",
                "请先核对 V001 迁移和菜单数据，禁止生成悬空父子关系");
        // V070 尚未执行时会自行补建系统管理目录，预检不能提前阻断跨版本升级。
        if (migrationApplied(connection, "070")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT '400', 'missing', 1
                    WHERE NOT EXISTS (
                      SELECT 1 FROM `sys_menu` WHERE `id` = '400'
                    )
                    """,
                    2,
                    "V075 缺少系统管理稳定目录",
                    "请先核对 V070 迁移和菜单数据，禁止生成悬空父子关系");
        }
        requireNoConflicts(
                connection,
                """
                SELECT `id`,
                       CONCAT_WS(':', COALESCE(`path`, ''), `menu_name`,
                                 `menu_type`, `deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu`
                WHERE `id` = '403'
                  AND NOT (
                    (
                      COALESCE(`path`, '') = '/system/group'
                      OR (
                        COALESCE(`path`, '') =
                            '/config/process-user-groups'
                        AND COALESCE(`parent_id`, '') = '300'
                        AND COALESCE(`menu_name`, '') = '流程用户组'
                      )
                    )
                    AND COALESCE(`menu_type`, '') = 'C'
                    AND COALESCE(`status`, '') = '0'
                    AND COALESCE(`visible`, '') = '0'
                    AND COALESCE(`deleted`, -1) = 0
                  )
                GROUP BY `id`, `path`, `menu_name`, `menu_type`, `deleted`
                LIMIT 10
                """,
                2,
                "V075 流程用户组固定 ID 403 已被其他资源占用",
                "请先恢复历史用户组菜单或迁移冲突资源，禁止覆盖现有导航");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, COALESCE(`path`, ''), COUNT(*)
                FROM `sys_menu`
                WHERE `path` = '/config/process-user-groups'
                  AND `id` <> '403'
                  AND COALESCE(`deleted`, 0) = 0
                GROUP BY `id`, `path`
                LIMIT 10
                """,
                2,
                "V076 流程用户组路径已被其他菜单占用",
                "请先合并重复入口或恢复稳定菜单 403");
        requireNoConflicts(
                connection,
                """
                SELECT menu_record.`id`,
                       CONCAT_WS(':', menu_record.`parent_id`,
                                 menu_record.`menu_name`,
                                 menu_record.`menu_type`,
                                 COALESCE(menu_record.`path`, ''),
                                 menu_record.`status`, menu_record.`visible`,
                                 menu_record.`deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu` menu_record
                JOIN (
                  SELECT 'dev_guide_list' AS `id`,
                         '列表字段扩展' AS `menu_name`,
                         '/system/dev-guide' AS `legacy_path`,
                         '/dev/manual/list-field-extension' AS `target_path`
                  UNION ALL SELECT 'list_field_guide_v2_001',
                                   '列表字段扩展2',
                                   '/system/list-field-guide',
                                   '/dev/manual/list-field-extension-v2'
                  UNION ALL SELECT 'custom_list_guide',
                                   '自定义列表组件',
                                   '/system/custom-list-guide',
                                   '/dev/manual/custom-list'
                  UNION ALL SELECT 'custom_form_guide',
                                   '自定义表单组件',
                                   '/system/custom-form-guide',
                                   '/dev/manual/custom-form'
                  UNION ALL SELECT 'flow_action_guide_menu_001',
                                   '流程动作',
                                   '/system/flow-action-guide',
                                   '/dev/manual/flow-actions'
                ) expected ON expected.`id` = menu_record.`id`
                WHERE NOT (
                  COALESCE(menu_record.`parent_id`, '') IN (
                    'dev_guide_dir', 'flow_setting_menu_001'
                  )
                  AND COALESCE(menu_record.`menu_name`, '') =
                      expected.`menu_name`
                  AND COALESCE(menu_record.`menu_type`, '') = 'C'
                  AND COALESCE(menu_record.`path`, '') IN (
                    expected.`legacy_path`, expected.`target_path`
                  )
                  AND COALESCE(menu_record.`status`, '') = '0'
                  AND COALESCE(menu_record.`visible`, '') = '0'
                  AND COALESCE(menu_record.`deleted`, -1) = 0
                )
                GROUP BY menu_record.`id`, menu_record.`parent_id`,
                         menu_record.`menu_name`, menu_record.`menu_type`,
                         menu_record.`path`, menu_record.`status`,
                         menu_record.`visible`, menu_record.`deleted`
                LIMIT 10
                """,
                2,
                "V075 开发手册固定 ID 已被其他资源占用",
                "请先恢复历史开发指南或迁移冲突资源，禁止覆盖现有导航");
        requireNoConflicts(
                connection,
                """
                SELECT menu_record.`id`, COALESCE(menu_record.`path`, ''),
                       COUNT(*)
                FROM `sys_menu` menu_record
                JOIN (
                  SELECT 'dev_guide_list' AS `id`,
                         '/dev/manual/list-field-extension' AS `target_path`
                  UNION ALL SELECT 'list_field_guide_v2_001',
                                   '/dev/manual/list-field-extension-v2'
                  UNION ALL SELECT 'custom_list_guide',
                                   '/dev/manual/custom-list'
                  UNION ALL SELECT 'custom_form_guide',
                                   '/dev/manual/custom-form'
                  UNION ALL SELECT 'flow_action_guide_menu_001',
                                   '/dev/manual/flow-actions'
                ) expected ON expected.`target_path` = menu_record.`path`
                WHERE menu_record.`id` <> expected.`id`
                  AND COALESCE(menu_record.`deleted`, 0) = 0
                GROUP BY menu_record.`id`, menu_record.`path`
                LIMIT 10
                """,
                2,
                "V076 开发手册路径已被其他菜单占用",
                "请先合并重复入口或恢复历史稳定菜单");
        requireNoConflicts(
                connection,
                """
                SELECT menu_record.`id`,
                       CONCAT_WS(':', menu_record.`menu_name`,
                                 menu_record.`menu_type`,
                                 COALESCE(menu_record.`path`, ''),
                                 menu_record.`status`, menu_record.`visible`,
                                 menu_record.`deleted`) AS semantics,
                       COUNT(*)
                FROM `sys_menu` menu_record
                JOIN (
                  SELECT 'list_column_template_menu_001' AS `id`,
                         '列表列模板' AS `menu_name`,
                         '/system/list-column-templates' AS `legacy_path`,
                         '/config/list-column-templates' AS `target_path`
                  UNION ALL SELECT 'extension_management_menu_001',
                                   '扩展管理', '/system/extensions',
                                   '/dev/extensions'
                  UNION ALL SELECT 'work_calendar_menu_001',
                                   '工作日历', '/system/work-calendars',
                                   '/system/work-calendars'
                  UNION ALL SELECT 'task_sla_policy_menu_001',
                                   'SLA策略', '/process/sla-policies',
                                   '/system/sla/policies'
                  UNION ALL SELECT 'task_sla_monitor_menu_001',
                                   'SLA监控', '/process/sla-monitor',
                                   '/system/sla/monitor'
                ) expected ON expected.`id` = menu_record.`id`
                WHERE NOT (
                  COALESCE(menu_record.`menu_name`, '') =
                      expected.`menu_name`
                  AND COALESCE(menu_record.`menu_type`, '') = 'C'
                  AND COALESCE(menu_record.`path`, '') IN (
                    expected.`legacy_path`, expected.`target_path`
                  )
                  AND COALESCE(menu_record.`status`, '') = '0'
                  AND COALESCE(menu_record.`visible`, '') = '0'
                  AND COALESCE(menu_record.`deleted`, -1) = 0
                )
                GROUP BY menu_record.`id`, menu_record.`menu_name`,
                         menu_record.`menu_type`, menu_record.`path`,
                         menu_record.`status`, menu_record.`visible`,
                         menu_record.`deleted`
                LIMIT 10
                """,
                2,
                "V075 待重组功能菜单语义错误或已停用",
                "请先恢复历史固定菜单，迁移不会用条件更新覆盖冲突资源");
        requireNoConflicts(
                connection,
                """
                SELECT menu_record.`id`, COALESCE(menu_record.`path`, ''),
                       COUNT(*)
                FROM `sys_menu` menu_record
                JOIN (
                  SELECT '300' AS `id`, '/config' AS `target_path`
                  UNION ALL SELECT 'flow_setting_menu_001', '/dev/manual'
                  UNION ALL SELECT 'list_column_template_menu_001',
                                   '/config/list-column-templates'
                  UNION ALL SELECT 'extension_management_menu_001',
                                   '/dev/extensions'
                  UNION ALL SELECT 'work_calendar_menu_001',
                                   '/system/work-calendars'
                  UNION ALL SELECT 'task_sla_policy_menu_001',
                                   '/system/sla/policies'
                  UNION ALL SELECT 'task_sla_monitor_menu_001',
                                   '/system/sla/monitor'
                  UNION ALL SELECT 'sla_management_dir_001',
                                   '/system/sla'
                ) expected ON expected.`target_path` = menu_record.`path`
                WHERE menu_record.`id` <> expected.`id`
                  AND COALESCE(menu_record.`deleted`, 0) = 0
                GROUP BY menu_record.`id`, menu_record.`path`
                LIMIT 10
                """,
                2,
                "V076 目标模块路径已被其他菜单占用",
                "请先合并重复入口或调整冲突菜单，再统一页面路由");
        requireNoConflicts(
                connection,
                """
                SELECT expected.`id`, 'missing', 1
                FROM (
                  SELECT 'extension_management_menu_001' AS `id`
                  UNION ALL SELECT 'flow_action_guide_menu_001'
                ) expected
                LEFT JOIN `sys_menu` menu_record
                  ON menu_record.`id` = expected.`id`
                WHERE menu_record.`id` IS NULL
                LIMIT 10
                """,
                2,
                "V075 缺少 V001 内置导航",
                "请先核对历史基线，禁止让扩展管理或流程动作静默缺失");
        if (migrationApplied(connection, "019")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT expected.`id`, 'missing', 1
                    FROM (
                      SELECT 'work_calendar_menu_001' AS `id`
                      UNION ALL SELECT 'task_sla_policy_menu_001'
                      UNION ALL SELECT 'task_sla_monitor_menu_001'
                    ) expected
                    LEFT JOIN `sys_menu` menu_record
                      ON menu_record.`id` = expected.`id`
                    WHERE menu_record.`id` IS NULL
                    LIMIT 10
                    """,
                    2,
                    "V075 缺少 V019 工作日历或 SLA 导航",
                    "请先恢复历史固定菜单，禁止创建空的 SLA 管理目录");
        }
        if (migrationApplied(connection, "030")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT 'list_column_template_menu_001', 'missing', 1
                    WHERE NOT EXISTS (
                      SELECT 1 FROM `sys_menu`
                      WHERE `id` = 'list_column_template_menu_001'
                    )
                    """,
                    2,
                    "V075 缺少 V030 列表列模板导航",
                    "请先恢复历史固定菜单，禁止静默跳过配置管理入口");
        }
        if (migrationApplied(connection, "047")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT 'list_field_guide_v2_001', 'missing', 1
                    WHERE NOT EXISTS (
                      SELECT 1 FROM `sys_menu`
                      WHERE `id` = 'list_field_guide_v2_001'
                    )
                    """,
                    2,
                    "V075 缺少 V047 列表字段扩展2导航",
                    "请先恢复历史固定菜单，禁止静默丢失开发手册入口");
        }
        requireNoConflicts(
                connection,
                """
                SELECT `id`, COALESCE(`menu_name`, ''), COUNT(*)
                FROM `sys_menu`
                WHERE `id` = 'sla_management_dir_001'
                GROUP BY `id`, `menu_name`
                LIMIT 10
                """,
                2,
                "V075 SLA 管理目录固定 ID 已被占用或存在部分迁移",
                "请先核对 Flyway 历史和冲突菜单，禁止覆盖既有资源");
    }

    /**
     * 在已执行 V075 的数据库上校验稳定菜单语义和 V076 目标路径。
     * 路由迁移只允许把已完成层级重组的内置菜单从旧地址切换到模块化地址。
     */
    private static void verifyNavigationMenuRouteMigrationPrerequisites(
            Connection connection) throws SQLException {
        requireNoConflicts(
                connection,
                """
                SELECT expected.`id`,
                       CASE
                         WHEN menu_record.`id` IS NULL THEN 'missing'
                         ELSE CONCAT_WS(':', menu_record.`parent_id`,
                           menu_record.`menu_name`, menu_record.`menu_type`,
                           COALESCE(menu_record.`path`, ''),
                           menu_record.`status`, menu_record.`visible`,
                           menu_record.`deleted`)
                       END AS semantics,
                       1
                FROM (
                  SELECT '300' AS `id`, '0' AS `parent_id`,
                         '配置管理' AS `menu_name`, 'M' AS `menu_type`,
                         '/entity' AS `legacy_path`, '/config' AS `target_path`
                  UNION ALL SELECT '403', '300', '流程用户组', 'C',
                                   '/system/group',
                                   '/config/process-user-groups'
                  UNION ALL SELECT 'list_column_template_menu_001', '300',
                                   '列表列模板', 'C',
                                   '/system/list-column-templates',
                                   '/config/list-column-templates'
                  UNION ALL SELECT 'flow_setting_menu_001', 'dev_guide_dir',
                                   '开发手册', 'M', '', '/dev/manual'
                  UNION ALL SELECT 'dev_guide_list',
                                   'flow_setting_menu_001',
                                   '列表字段扩展', 'C', '/system/dev-guide',
                                   '/dev/manual/list-field-extension'
                  UNION ALL SELECT 'list_field_guide_v2_001',
                                   'flow_setting_menu_001',
                                   '列表字段扩展2', 'C',
                                   '/system/list-field-guide',
                                   '/dev/manual/list-field-extension-v2'
                  UNION ALL SELECT 'custom_list_guide',
                                   'flow_setting_menu_001',
                                   '自定义列表组件', 'C',
                                   '/system/custom-list-guide',
                                   '/dev/manual/custom-list'
                  UNION ALL SELECT 'custom_form_guide',
                                   'flow_setting_menu_001',
                                   '自定义表单组件', 'C',
                                   '/system/custom-form-guide',
                                   '/dev/manual/custom-form'
                  UNION ALL SELECT 'flow_action_guide_menu_001',
                                   'flow_setting_menu_001', '流程动作', 'C',
                                   '/system/flow-action-guide',
                                   '/dev/manual/flow-actions'
                  UNION ALL SELECT 'extension_management_menu_001',
                                   'dev_guide_dir', '扩展管理', 'C',
                                   '/system/extensions', '/dev/extensions'
                  UNION ALL SELECT 'sla_management_dir_001', '400',
                                   'SLA管理', 'M', '', '/system/sla'
                  UNION ALL SELECT 'task_sla_policy_menu_001',
                                   'sla_management_dir_001', 'SLA策略', 'C',
                                   '/process/sla-policies',
                                   '/system/sla/policies'
                  UNION ALL SELECT 'task_sla_monitor_menu_001',
                                   'sla_management_dir_001', 'SLA监控', 'C',
                                   '/process/sla-monitor',
                                   '/system/sla/monitor'
                ) expected
                LEFT JOIN `sys_menu` menu_record
                  ON menu_record.`id` = expected.`id`
                WHERE menu_record.`id` IS NULL
                   OR NOT (
                     COALESCE(menu_record.`parent_id`, '') =
                         expected.`parent_id`
                     AND COALESCE(menu_record.`menu_name`, '') =
                         expected.`menu_name`
                     AND COALESCE(menu_record.`menu_type`, '') =
                         expected.`menu_type`
                     AND COALESCE(menu_record.`path`, '') IN (
                       expected.`legacy_path`, expected.`target_path`
                     )
                     AND COALESCE(menu_record.`status`, '') = '0'
                     AND COALESCE(menu_record.`visible`, '') = '0'
                     AND COALESCE(menu_record.`deleted`, -1) = 0
                   )
                LIMIT 10
                """,
                2,
                "V076 路由迁移目标菜单缺失、语义错误或已停用",
                "请先恢复 V075 完成后的菜单层级，禁止按固定 ID 覆盖冲突资源");
        requireNoConflicts(
                connection,
                """
                SELECT menu_record.`id`, COALESCE(menu_record.`path`, ''),
                       COUNT(*)
                FROM `sys_menu` menu_record
                JOIN (
                  SELECT '300' AS `id`, '/config' AS `target_path`
                  UNION ALL SELECT '403', '/config/process-user-groups'
                  UNION ALL SELECT 'list_column_template_menu_001',
                                   '/config/list-column-templates'
                  UNION ALL SELECT 'flow_setting_menu_001', '/dev/manual'
                  UNION ALL SELECT 'dev_guide_list',
                                   '/dev/manual/list-field-extension'
                  UNION ALL SELECT 'list_field_guide_v2_001',
                                   '/dev/manual/list-field-extension-v2'
                  UNION ALL SELECT 'custom_list_guide',
                                   '/dev/manual/custom-list'
                  UNION ALL SELECT 'custom_form_guide',
                                   '/dev/manual/custom-form'
                  UNION ALL SELECT 'flow_action_guide_menu_001',
                                   '/dev/manual/flow-actions'
                  UNION ALL SELECT 'extension_management_menu_001',
                                   '/dev/extensions'
                  UNION ALL SELECT 'sla_management_dir_001', '/system/sla'
                  UNION ALL SELECT 'task_sla_policy_menu_001',
                                   '/system/sla/policies'
                  UNION ALL SELECT 'task_sla_monitor_menu_001',
                                   '/system/sla/monitor'
                ) expected ON expected.`target_path` = menu_record.`path`
                WHERE menu_record.`id` <> expected.`id`
                  AND COALESCE(menu_record.`deleted`, 0) = 0
                GROUP BY menu_record.`id`, menu_record.`path`
                LIMIT 10
                """,
                2,
                "V076 目标模块路径已被其他菜单占用",
                "请先合并重复入口或调整冲突菜单，再统一页面路由");
    }

    /**
     * 在 V079 的非事务 DDL 前检查目标表和菜单资源。
     *
     * <p>从较早版本一次升级到最新版本时，系统管理目录可能尚未由 V070
     * 创建，因此只校验已存在目录的语义；若 V070 已记录成功，则目录缺失
     * 同样属于损坏状态。</p>
     */
    private static void verifyExternalSystemManagementMigrationPrerequisites(
            Connection connection) throws SQLException {
        List<String> partialArtifacts = new ArrayList<>();
        for (String table : List.of(
                "sys_external_system",
                "sys_external_system_parameter")) {
            if (tableExists(connection, table)) {
                partialArtifacts.add(table);
            }
        }
        failIfSamples(
                partialArtifacts,
                "V079 检测到目标表已存在的部分迁移状态",
                "请先核对数据库与 Flyway 历史并制定显式恢复方案，禁止在非事务 DDL 上续跑");

        // 全新数据库会在同一次 Flyway 调用中先执行 V001，此时尚无菜单表。
        if (!tableExists(connection, "sys_menu")) {
            return;
        }
        requireNoConflicts(
                connection,
                """
                SELECT `id`,
                       CONCAT_WS(':', `parent_id`, `menu_name`, `menu_type`,
                                 COALESCE(`path`, ''),
                                 COALESCE(`perm`, ''), `status`, `visible`,
                                 `deleted`) AS semantics,
                       1
                FROM `sys_menu`
                WHERE `id` = '400'
                  AND NOT (
                    COALESCE(`parent_id`, '') = '0'
                    AND COALESCE(`menu_name`, '') = '系统管理'
                    AND COALESCE(`menu_type`, '') = 'M'
                    AND COALESCE(`path`, '') IN ('/system', 'system')
                    AND COALESCE(TRIM(`perm`), '') = ''
                    AND COALESCE(`status`, '') = '0'
                    AND COALESCE(`visible`, '') = '0'
                    AND COALESCE(`deleted`, -1) = 0
                  )
                LIMIT 10
                """,
                2,
                "V079 既有系统管理父目录 400 语义错误或已停用",
                "请先恢复为启用的系统管理目录；迁移不会覆盖既有菜单内容");
        if (migrationApplied(connection, "070")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT '400', 'missing', 1
                    WHERE NOT EXISTS (
                      SELECT 1 FROM `sys_menu` WHERE `id` = '400'
                    )
                    """,
                    2,
                    "V079 缺少系统管理父目录 400",
                    "请先恢复 V070 创建的系统管理目录，禁止生成悬空菜单");
        }
        requireNoConflicts(
                connection,
                """
                SELECT `id`, COALESCE(`menu_name`, ''), 1
                FROM `sys_menu`
                WHERE `id` IN (
                  'external_system_menu_001',
                  'external_system_view_permission_001',
                  'external_system_manage_permission_001'
                )
                LIMIT 10
                """,
                2,
                "V079 外部系统菜单固定 ID 已被占用或存在部分迁移",
                "请先迁移冲突资源或核对 Flyway 历史，禁止覆盖既有菜单");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, COALESCE(`path`, ''), 1
                FROM `sys_menu`
                WHERE `path` = '/system/external-systems'
                LIMIT 10
                """,
                2,
                "V079 外部系统菜单路径已被占用",
                "请先调整冲突路由，禁止生成两个相同的系统管理页面入口");
        requireNoConflicts(
                connection,
                """
                SELECT `id`, COALESCE(`perm`, ''), 1
                FROM `sys_menu`
                WHERE `perm` IN (
                  'system:external-system:view',
                  'system:external-system:manage'
                )
                LIMIT 10
                """,
                2,
                "V079 外部系统权限标识已被占用",
                "请先核对冲突权限语义，禁止复用已有权限标识");

        // 新菜单不存在时不应已有对应授权，否则 NOT EXISTS 会掩盖孤儿数据。
        if (tableExists(connection, "sys_role_menu")) {
            requireNoConflicts(
                    connection,
                    """
                    SELECT `id`, `menu_id`, 1
                    FROM `sys_role_menu`
                    WHERE `menu_id` IN (
                      'external_system_menu_001',
                      'external_system_view_permission_001',
                      'external_system_manage_permission_001'
                    )
                    LIMIT 10
                    """,
                    2,
                    "V079 检测到外部系统菜单的孤儿授权或部分迁移状态",
                    "请先核对角色授权与 Flyway 历史，禁止静默复用残留授权");
        }
    }

    /**
     * 使用 parent_id 而不是冗余 path 校验完整父链及用户 org/dept 不变量。
     */
    private static void verifyOrganizationAndUserHierarchy(
            Connection connection) throws SQLException {
        Map<String, OrganizationRow> organizations = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT `id`, `parent_id`, `type`, `status`
                     FROM `sys_organization`
                     WHERE `deleted` = 0
                     """)) {
            while (rows.next()) {
                OrganizationRow row = new OrganizationRow(
                        rows.getString("id"),
                        rows.getString("parent_id"),
                        rows.getString("type"),
                        rows.getString("status"));
                organizations.put(row.id(), row);
            }
        }

        List<String> hierarchyConflicts = new ArrayList<>();
        for (OrganizationRow organization : organizations.values()) {
            String failure = hierarchyFailure(organization.id(), organizations);
            if (failure != null && hierarchyConflicts.size() < SAMPLE_LIMIT) {
                hierarchyConflicts.add(organization.id() + "/" + failure);
            }
        }
        failIfSamples(
                hierarchyConflicts,
                "V069 检测到无效组织父链",
                "请先修复悬空 parent_id、组织环或超过 32 层的异常父链");

        List<String> membershipConflicts = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT `id`, `org_id`, `dept_id`
                     FROM `sys_user`
                     WHERE `deleted` = 0
                     """)) {
            while (rows.next() && membershipConflicts.size() < SAMPLE_LIMIT) {
                String userId = rows.getString("id");
                String orgId = trimToNull(rows.getString("org_id"));
                String deptId = trimToNull(rows.getString("dept_id"));
                String failure = membershipFailure(
                        orgId, deptId, organizations);
                if (failure != null) {
                    membershipConflicts.add(
                            userId + "/org=" + orgId + "/dept=" + deptId
                                    + "/" + failure);
                }
            }
        }
        failIfSamples(
                membershipConflicts,
                "V069 检测到用户组织/部门归属不一致",
                "请先确保 org_id 指向启用组织、dept_id 指向其范围内的启用部门");
    }

    private static String hierarchyFailure(
            String startId,
            Map<String, OrganizationRow> organizations) {
        Set<String> visited = new HashSet<>();
        String currentId = startId;
        for (int depth = 0; depth < 32; depth++) {
            if (!visited.add(currentId)) {
                return "cycle=" + currentId;
            }
            OrganizationRow current = organizations.get(currentId);
            if (current == null) {
                return "missing=" + currentId;
            }
            String parentId = trimToNull(current.parentId());
            if (parentId == null || "0".equals(parentId)) {
                return null;
            }
            currentId = parentId;
        }
        return "depth>32";
    }

    private static String membershipFailure(
            String orgId,
            String deptId,
            Map<String, OrganizationRow> organizations) {
        if (orgId == null) {
            return deptId == null ? null : "department-without-organization";
        }
        OrganizationRow organization = organizations.get(orgId);
        if (organization == null
                || !"org".equals(organization.type())
                || !"0".equals(organization.status())) {
            return "organization-missing-disabled-or-wrong-type";
        }
        if (deptId == null) {
            return null;
        }
        OrganizationRow department = organizations.get(deptId);
        if (department == null
                || !"dept".equals(department.type())
                || !"0".equals(department.status())) {
            return "department-missing-disabled-or-wrong-type";
        }

        Set<String> visited = new HashSet<>();
        String currentId = deptId;
        for (int depth = 0; depth < 32; depth++) {
            if (!visited.add(currentId)) {
                return "department-chain-cycle";
            }
            if (orgId.equals(currentId)) {
                return null;
            }
            OrganizationRow current = organizations.get(currentId);
            if (current == null) {
                return "department-chain-missing-node";
            }
            String parentId = trimToNull(current.parentId());
            if (parentId == null || "0".equals(parentId)) {
                return "department-outside-organization";
            }
            currentId = parentId;
        }
        return "department-chain-depth>32";
    }

    private static void failIfSamples(
            List<String> samples,
            String title,
            String remediation) {
        if (!samples.isEmpty()) {
            throw new IllegalStateException(
                    title + "；冲突样例=" + samples + "。" + remediation
                            + "。预检在 Flyway.migrate() 前终止，未写入失败的迁移历史。");
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static boolean migrationApplied(
            Connection connection,
            String version) throws SQLException {
        if (!tableExists(connection, "flyway_schema_history")) {
            return false;
        }
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM `flyway_schema_history`
                WHERE (`version` = ? OR `version` = ?)
                  AND `success` = 1
                """)) {
            statement.setString(1, version);
            statement.setString(2, String.valueOf(
                    Integer.parseInt(version)));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getLong(1) > 0;
            }
        }
    }

    private static boolean tableExists(
            Connection connection,
            String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                connection.getCatalog(),
                null,
                tableName,
                new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean columnExists(
            Connection connection,
            String tableName,
            String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(
                connection.getCatalog(),
                null,
                tableName,
                columnName)) {
            return columns.next();
        }
    }

    private static boolean indexExists(
            Connection connection,
            String tableName,
            String indexName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(),
                null,
                tableName,
                false,
                false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(
                        indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void requireNoConflicts(
            Connection connection,
            String query,
            int keyColumnCount,
            String title,
            String remediation) throws SQLException {
        List<String> samples = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(query)) {
            while (rows.next() && samples.size() < SAMPLE_LIMIT) {
                List<String> key = new ArrayList<>();
                for (int index = 1; index <= keyColumnCount; index++) {
                    key.add(String.valueOf(rows.getObject(index)));
                }
                key.add("count=" + rows.getLong(keyColumnCount + 1));
                samples.add(String.join("/", key));
            }
        }
        if (!samples.isEmpty()) {
            throw new IllegalStateException(
                    title + "；冲突样例=" + samples + "。" + remediation
                            + "。预检在 Flyway.migrate() 前终止，未写入失败的迁移历史。");
        }
    }

    private record OrganizationRow(
            String id,
            String parentId,
            String type,
            String status) {
    }
}
