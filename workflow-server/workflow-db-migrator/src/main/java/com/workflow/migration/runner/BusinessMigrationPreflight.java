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
