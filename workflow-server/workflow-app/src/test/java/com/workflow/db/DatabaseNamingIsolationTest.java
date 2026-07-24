package com.workflow.db;

import com.workflow.controller.EntityFlowStatusController;
import com.workflow.controller.FlowActionController;
import com.workflow.controller.FlowActionExecutionController;
import com.workflow.controller.FlowActionHandlerController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库命名隔离架构守护测试。
 *
 * <p>验证迁移脚本完成静态表重命名、运行时 Java 源码不再引用旧表名、
 * 控制器仅暴露规范化接口、前端仅使用规范化客户端，以及动态表使用可移植 Unicode 排序规则。</p>
 */
class DatabaseNamingIsolationTest {

    /** 匹配遗留静态表名的正则：@TableName 注解与 SQL FROM/UPDATE/DELETE 语句 */
    private static final Pattern LEGACY_RUNTIME_REFERENCE = Pattern.compile(
            "@TableName\\(\\\"(entity_data|node_config|assignee_config|form_config|"
                    + "form_field_config|flow_action|flow_action_definition|"
                    + "flow_action_execution|entity_flow_status_mapping)\\\"\\)"
                    + "|(?:FROM|UPDATE|DELETE FROM)\\s+"
                    + "(entity_data|node_config|assignee_config|form_config|"
                    + "form_field_config|flow_action|flow_action_definition|"
                    + "flow_action_execution|entity_flow_status_mapping)\\b");

    /**
     * V017 迁移应重命名静态表并创建迁移日志表。
     *
     * <p>断言 SQL 含全部表重命名语句与 entity_table_migration_log 建表语句，
     * 且 EntityDefinitionMapper 使用 table_name AS physical_table_name 别名。</p>
     */
    @Test
    void migrationShouldRenameStaticTablesAndCreateDynamicMigrationLog() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V017__isolate_database_table_names.sql"));

        for (String rename : List.of(
                "'entity_data', 'runtime_entity_record'",
                "'node_config', 'process_node_config'",
                "'assignee_config', 'process_node_assignee'",
                "'form_config', 'process_form_config'",
                "'form_field_config', 'process_form_field_config'",
                "'flow_action', 'process_action'",
                "'flow_action_definition', 'process_action_definition'",
                "'flow_action_execution', 'process_action_execution'",
                "'entity_flow_status_mapping', 'process_entity_status_mapping'")) {
            assertTrue(sql.contains(rename), "V017 缺少表重命名: " + rename);
        }
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `entity_table_migration_log`"));

        String mapper = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/mapper/EntityDefinitionMapper.java"));
        assertTrue(mapper.contains("table_name AS physical_table_name"));
    }

    /**
     * 运行时 Java 源码不应引用遗留静态表名。
     *
     * <p>遍历 entity/process/action/migration/system 模块的 Java 文件，
     * 断言无文件匹配旧表名正则。</p>
     */
    @Test
    void runtimeJavaSourcesShouldNotUseLegacyStaticTableNames() throws Exception {
        for (Path module : List.of(
                Path.of("../workflow-entity/src/main/java"),
                Path.of("../workflow-process/src/main/java"),
                Path.of("../workflow-action/src/main/java"),
                Path.of("../workflow-migration/src/main/java"),
                Path.of("../workflow-system/src/main/java"))) {
            try (var files = Files.walk(module)) {
                List<Path> violations = files
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> {
                            try {
                                return LEGACY_RUNTIME_REFERENCE
                                        .matcher(Files.readString(path))
                                        .find();
                            } catch (Exception exception) {
                                throw new IllegalStateException(exception);
                            }
                        })
                        .toList();
                assertTrue(violations.isEmpty(), "仍存在旧表运行时引用: " + violations);
            }
        }
    }

    /** 各控制器应仅暴露规范化的 process API 路径 */
    @Test
    void controllersShouldExposeOnlyCanonicalProcessApis() {
        assertRequestMapping(FlowActionController.class, "/api/process-actions");
        assertRequestMapping(
                FlowActionHandlerController.class,
                "/api/process-action-handlers");
        assertRequestMapping(
                FlowActionExecutionController.class,
                "/api/process-action-executions");
        assertRequestMapping(
                EntityFlowStatusController.class,
                "/api/process-entity-status-mappings");
    }

    /**
     * 前端应仅使用规范化的流程动作客户端 API。
     *
     * <p>断言 processAction.js 存在且含新端点，flowAction.js 不存在，
     * 且不含旧端点路径。</p>
     */
    @Test
    void frontendShouldUseOnlyCanonicalProcessActionClient() throws Exception {
        Path webRoot = Path.of("../../workflow-web/src");
        assertTrue(Files.exists(webRoot.resolve("api/processAction.js")));
        assertFalse(Files.exists(webRoot.resolve("api/flowAction.js")));

        String api = Files.readString(webRoot.resolve("api/processAction.js"));
        for (String endpoint : List.of(
                "/process-actions",
                "/process-action-handlers",
                "/process-action-executions")) {
            assertTrue(api.contains(endpoint), "前端缺少新流程动作接口: " + endpoint);
        }
        assertFalse(api.contains("/flow-actions"));
        assertFalse(api.contains("/flow-action-handlers"));
        assertFalse(api.contains("/flow-action-executions"));
    }

    /**
     * 实体列表运行时应仅使用新的作用域表与 API。
     *
     * <p>断言前端使用 entity-lists 与 entity-list-scopes 端点，
     * 不含旧 entity-list-permission 端点，且 V018 迁移创建作用域表并删除旧权限表。</p>
     */
    @Test
    void entityListRuntimeShouldUseOnlyNewScopeTablesAndApis() throws Exception {
        Path webRoot = Path.of("../../workflow-web/src");
        String runtimeApi = Files.readString(webRoot.resolve("api/entityListRuntime.js"));
        String scopeApi = Files.readString(webRoot.resolve("api/entityListScope.js"));
        assertTrue(runtimeApi.contains("/entity-lists/"));
        assertTrue(scopeApi.contains("/entity-list-scopes"));
        assertFalse(scopeApi.contains("/entity-list-permission"));

        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V018__unify_entity_list_runtime_and_scope.sql"));
        assertTrue(migration.contains("entity_list_scope_policy"));
        assertTrue(migration.contains("entity_list_scope_binding"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS entity_list_permission"));
    }

    /**
     * 动态实体表应使用可移植的 Unicode 排序规则。
     *
     * <p>断言 V026 迁移含 biz_% 通配与 utf8mb4_unicode_ci 转换，
     * 且 DynamicTableService 与 EntityRecordTeamService 源码含 COLLATE 声明。</p>
     */
    @Test
    void dynamicEntityTablesShouldUsePortableUnicodeCollation() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V026__normalize_dynamic_entity_collation.sql"));
        assertTrue(migration.contains("table_name LIKE 'biz\\\\_%'"));
        assertTrue(migration.contains(
                "CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"));
        assertTrue(migration.contains("remaining_mixed_biz_collation"));

        String dynamicTableService = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/service/DynamicTableService.java"));
        String teamService = Files.readString(Path.of(
                "../workflow-entity/src/main/java/com/workflow/service/EntityRecordTeamService.java"));
        assertTrue(dynamicTableService.contains("COLLATE=utf8mb4_unicode_ci"));
        assertTrue(teamService.contains("COLLATE=utf8mb4_unicode_ci"));
    }

    /**
     * 前端应使用生命周期模式与规范化工作流绑定 API。
     *
     * <p>断言 entity.js 含 workflow-binding 与 lifecycle-mode 端点、不含旧绑定端点，
     * 且 EntityList.vue 使用 lifecycleMode/storageMode、不含 enableProcess。</p>
     */
    @Test
    void frontendShouldUseLifecycleAndCanonicalWorkflowBindingApi() throws Exception {
        Path webRoot = Path.of("../../workflow-web/src");
        String entityApi = Files.readString(webRoot.resolve("api/entity.js"));
        assertTrue(entityApi.contains("/workflow-binding"));
        assertTrue(entityApi.contains("/lifecycle-mode"));
        assertFalse(entityApi.contains("/bind-process/"));

        String entityList = Files.readString(webRoot.resolve("views/EntityList.vue"));
        assertTrue(entityList.contains("lifecycleMode"));
        assertTrue(entityList.contains("storageMode"));
        assertFalse(entityList.contains("enableProcess"));
    }

    /**
     * 断言控制器的 @RequestMapping 注解值为预期路径。
     *
     * @param controllerType 控制器类
     * @param expected 期望的请求路径
     */
    private void assertRequestMapping(Class<?> controllerType, String expected) {
        RequestMapping mapping = controllerType.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{expected}, mapping.value());
    }
}
