package com.workflow.entity.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.migration.application.*;
import com.workflow.migration.infrastructure.persistence.mapper.*;
import com.workflow.migration.infrastructure.persistence.record.*;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.currentTable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 调用真实配置迁移服务及 Mapper；只复制相关结构到随机表，不运行部署或数据迁移。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlConfigMigrationQueryDatabaseTest {
    @Test void latestHistoryRollbackAndBaselineLookupsKeepScopeDeletionAndStableOrdering() throws Exception {
        try (var f = new Fixture()) {
            currentTable(f, "config_migration_asset");
            var h = new Harness(f, ConfigMigrationAssetMapper.class);
            var mapper = h.mapper(ConfigMigrationAssetMapper.class);
            var dependencies = Map.<Class<?>, Object>of(ConfigMigrationAssetMapper.class, mapper);
            var assets = service(ConfigMigrationAssetService.class, h, dependencies);
            var packages = service(ConfigMigrationPackageService.class, h, dependencies);
            var importer = service(ConfigMigrationImportApplyService.class, h, dependencies);
            insertAsset(h, "old", "ENTITY", "asset", 1, "before", 0);
            insertAsset(h, "a", "ENTITY", "asset", 2, "before", 0);
            insertAsset(h, "b", "ENTITY", "asset", 2, "after", 0);
            insertAsset(h, "deleted", "ENTITY", "asset", 3, "before", 1);
            insertAsset(h, "other-type", "PROCESS", "asset", 4, "before", 0);
            insertAsset(h, "other-key", "ENTITY", "other", 4, "before", 0);
            assertEquals("b", assets.findLatest("ENTITY", "asset").getId());
            assertNull(assets.findLatest("ENTITY", "asset' OR 1=1 --"));
            ConfigMigrationAsset history = ReflectionTestUtils.invokeMethod(assets, "findByHistory", "ENTITY", "history-a");
            assertEquals("a", history.getId());
            assertNull(ReflectionTestUtils.invokeMethod(assets, "findByHistory", "ENTITY", "history-deleted"));
            var item = new ConfigImportItem(); item.setAssetType("ENTITY"); item.setBusinessKey("asset"); item.setTargetBeforeHash("before");
            ConfigMigrationAsset previous = ReflectionTestUtils.invokeMethod(importer, "previousAsset", item);
            assertEquals("a", previous.getId());
            var baseline = new ConfigAssetBaseline(); baseline.setTargetVersion(2); baseline.setTargetHash("before");
            ConfigMigrationAsset target = ReflectionTestUtils.invokeMethod(packages, "findBaselineTarget", "ENTITY", "asset", baseline);
            assertEquals("b", target.getId());
            baseline.setTargetVersion(99);
            target = ReflectionTestUtils.invokeMethod(packages, "findBaselineTarget", "ENTITY", "asset", baseline);
            assertEquals("a", target.getId());
            baseline.setTargetHash("missing");
            assertNull(ReflectionTestUtils.invokeMethod(packages, "findBaselineTarget", "ENTITY", "asset", baseline));
        }
    }

    @Test void uniqueEnvironmentMappingsKeepTypeEnabledAndLiteralKeySemanticsAcrossAllCallers() throws Exception {
        try (var f = new Fixture()) {
            currentTable(f, "config_environment_mapping");
            var h = new Harness(f, ConfigEnvironmentMappingMapper.class);
            h.jdbc.update("INSERT INTO config_environment_mapping(id,source_type,source_key,target_key,enabled) VALUES "
                    + "('p','PROCESS','source','process-target',1),('e','ENTITY','source','entity-target',1),('d','PROCESS','disabled','disabled-target',0)");
            h.jdbc.update("INSERT INTO config_environment_mapping(id,source_type,source_key,target_key) VALUES ('q','PROCESS',?,'literal-target')", "x' OR 1=1 --");
            var dependencies = Map.<Class<?>, Object>of(ConfigEnvironmentMappingMapper.class, h.mapper(ConfigEnvironmentMappingMapper.class));
            for (Class<?> type : List.of(ConfigMigrationPackageService.class, ConfigMigrationImportApplyService.class)) {
                Object target = service(type, h, dependencies);
                assertEquals("process-target", ReflectionTestUtils.invokeMethod(target, "mappedKey", "PROCESS", "source"));
                assertEquals("entity-target", ReflectionTestUtils.invokeMethod(target, "mappedKey", "ENTITY", "source"));
                assertEquals("disabled", ReflectionTestUtils.invokeMethod(target, "mappedKey", "PROCESS", "disabled"));
                assertEquals("missing", ReflectionTestUtils.invokeMethod(target, "mappedKey", "PROCESS", "missing"));
                assertEquals("literal-target", ReflectionTestUtils.invokeMethod(target, "mappedKey", "PROCESS", "x' OR 1=1 --"));
            }
            var coordinator = service(ConfigMigrationProcessLockCoordinator.class, h, dependencies);
            assertEquals("process-target", ReflectionTestUtils.invokeMethod(coordinator, "mappedProcessKey", "source"));
            assertEquals("disabled", ReflectionTestUtils.invokeMethod(coordinator, "mappedProcessKey", "disabled"));
        }
    }

    @Test void scopedBaselineComparisonUsesTheFullUniqueKeyWithoutHidingOtherScopes() throws Exception {
        try (var f = new Fixture()) {
            currentTable(f, "config_asset_baseline");
            var h = new Harness(f, ConfigAssetBaselineMapper.class);
            h.jdbc.execute(migration("V036__config_migration_scoped_baseline.sql"));
            h.jdbc.update("INSERT INTO config_asset_baseline(id,asset_type,business_key,scope_key,source_version,source_hash,target_hash,import_package_id) VALUES "
                    + "('full','ENTITY','asset','FULL',1,'source-old','target','batch'),('scope','ENTITY','asset','SCOPED',1,'source-new','drift','batch')");
            var assets = mock(ConfigMigrationAssetService.class);
            var asset = new ConfigMigrationAsset(); asset.setSourceVersion(2); asset.setSnapshotJson("{}"); asset.setContentHash("target");
            when(assets.findLatest("ENTITY", "asset")).thenReturn(asset);
            var scope = new java.util.concurrent.atomic.AtomicReference<>("FULL");
            var codec = mock(ConfigMigrationPackageCodec.class, invocation -> switch (invocation.getMethod().getName()) {
                case "selectionOf" -> Map.of();
                case "hashSelectedSnapshot" -> "target";
                case "selectionScopeKey" -> scope.get();
                default -> null;
            });
            Class<?> documentsType = Class.forName("com.workflow.migration.application.ConfigMigrationPackageDocumentSupport");
            var constructor = documentsType.getDeclaredConstructor(ObjectMapper.class); constructor.setAccessible(true);
            Object documents = constructor.newInstance(h.json);
            var target = service(ConfigMigrationPackageService.class, h, Map.of(
                    ConfigAssetBaselineMapper.class, h.mapper(ConfigAssetBaselineMapper.class), ConfigMigrationAssetService.class, assets,
                    ConfigMigrationPackageCodec.class, codec, documentsType, documents));
            var item = new ConfigImportItem(); item.setAssetType("ENTITY"); item.setBusinessKey("asset"); item.setSnapshotJson("{}"); item.setSourceHash("source-new");
            assertEquals("SOURCE_NEWER", ReflectionTestUtils.invokeMethod(target, "compare", item));
            scope.set("SCOPED");
            assertEquals("LOCAL_CHANGED", ReflectionTestUtils.invokeMethod(target, "compare", item));
            item.setSourceHash("another-source");
            assertEquals("CONFLICT", ReflectionTestUtils.invokeMethod(target, "compare", item));
        }
    }

    @Test void dictionaryLookupExcludesDeletedTwinAndRepeatedExportReusesTheAsset() throws Exception {
        try (var f = new Fixture()) {
            for (String table : List.of("sys_dict", "sys_dict_item", "config_migration_asset")) currentTable(f, table);
            var h = new Harness(f, SysDictMapper.class, SysDictItemMapper.class, ConfigMigrationAssetMapper.class);
            h.jdbc.update("INSERT INTO sys_dict(id,dict_code,dict_name,deleted) VALUES ('active','priority','优先级',0),('deleted','priority','旧优先级',1)");
            var target = service(ConfigMigrationAssetService.class, h, Map.of(SysDictMapper.class, h.mapper(SysDictMapper.class),
                    SysDictItemMapper.class, h.mapper(SysDictItemMapper.class), ConfigMigrationAssetMapper.class, h.mapper(ConfigMigrationAssetMapper.class)));
            ConfigMigrationAsset first = target.ensureDictionaryAsset("priority");
            assertNotNull(first); assertEquals("优先级", first.getAssetName());
            assertEquals(first.getId(), target.ensureDictionaryAsset("priority").getId());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM config_migration_asset", Integer.class));
            assertNull(target.ensureDictionaryAsset("absent"));
            assertNull(target.ensureDictionaryAsset("priority' OR 1=1 --"));
        }
    }

    @Test void interfaceImportMatchesRequestedVersionInsteadOfOverwritingAnotherVersion() throws Exception {
        try (var f = new Fixture()) {
            var h = extensionHarness(f);
            seedExtensions(h);
            var saver = mock(UiInterfaceExtensionService.class);
            var requests = new ArrayList<UiExtensionDefinitionSaveRequest>();
            when(saver.save(any())).thenAnswer(invocation -> {
                UiExtensionDefinitionSaveRequest request = invocation.getArgument(0); requests.add(request);
                var result = new UiExtensionDefinition(); result.setId(request.getId()); return result;
            });
            var target = service(ConfigMigrationImportApplyService.class, h, Map.of(
                    UiExtensionDefinitionMapper.class, h.mapper(UiExtensionDefinitionMapper.class), UiInterfaceExtensionService.class, saver));
            var entity = new EntityDefinition(); entity.setId("entity"); entity.setEntityCode("asset");
            for (Map<String, Object> definition : List.<Map<String, Object>>of(Map.of("version", 2), Map.of(), Map.of("version", 3))) {
                ReflectionTestUtils.invokeMethod(target, "saveInterfaceExtension", entity, definition, "shared", null, false);
            }
            assertEquals("interface-v2", requests.get(0).getId());
            assertEquals(2, requests.get(0).getVersion()); assertEquals(7, requests.get(0).getExpectedRevision());
            assertEquals("interface-v1", requests.get(1).getId()); assertEquals(1, requests.get(1).getVersion());
            assertNull(requests.get(2).getId()); assertNull(requests.get(2).getExpectedRevision());
        }
    }

    @Test void extensionExistenceAndLegacyReferenceResolutionHandleMultipleVersionsAndTypes() throws Exception {
        try (var f = new Fixture()) {
            var h = extensionHarness(f); seedExtensions(h);
            var definitions = h.mapper(UiExtensionDefinitionMapper.class);
            var packages = service(ConfigMigrationPackageService.class, h, Map.of(UiExtensionDefinitionMapper.class, definitions));
            for (String type : List.of("CUSTOM_COMPONENT", "INTERFACE", "INTERFACE_SERVICE")) {
                assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(packages, "isDependencyResolved", Map.of(), type, "shared", Map.of()));
                assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(packages, "isDependencyResolved", Map.of(), type, "missing", Map.of()));
            }
            assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(packages, "isDependencyResolved", Map.of("version", 99), "CUSTOM_COMPONENT", "shared", Map.of()));
            var entity = new EntityDefinition(); entity.setId("entity"); entity.setEntityCode("asset"); entity.setEntityName("实体");
            var form = new EntityForm(); form.setId("form"); form.setFormKey("edit"); form.setFormName("表单");
            var entityMapper = mock(EntityDefinitionMapper.class); when(entityMapper.findByEntityCode("asset")).thenReturn(Optional.of(entity));
            var formMapper = mock(EntityFormMapper.class); when(formMapper.selectByEntityIdAndFormKey("entity", "edit")).thenReturn(form);
            var importer = service(ConfigMigrationImportApplyService.class, h, Map.of(UiExtensionDefinitionMapper.class, definitions,
                    EntityDefinitionMapper.class, entityMapper, EntityFormMapper.class, formMapper));
            var config = Map.of("target", Map.of("entityCode", "asset", "contentType", "FORM", "contentKey", "edit"),
                    "specialHandling", Map.of("interfaceService", Map.of("extensionCode", "shared"), "actionServices", List.of(Map.of("extensionCode", "shared"))));
            Map<String, Object> resolved = ReflectionTestUtils.invokeMethod(importer, "resolvePortableViewComposition", entity, Map.of("config", config), Map.of());
            var node = h.json.valueToTree(resolved).path("config").path("specialHandling");
            assertEquals("interface-v2", node.path("interfaceService").path("extensionId").asText());
            assertEquals("interface-v2", node.path("actionServices").get(0).path("extensionId").asText());
            var assets = service(ConfigMigrationAssetService.class, h, Map.of(UiExtensionDefinitionMapper.class, definitions,
                    EntityDefinitionMapper.class, entityMapper, EntityFormMapper.class, formMapper));
            Set<String> sources = new HashSet<>();
            var exportConfig = Map.of("target", config.get("target"), "specialHandling", Map.of("interfaceService", Map.of("extensionCode", "shared")));
            ReflectionTestUtils.invokeMethod(assets, "portableViewCompositions", Map.of("viewCompositions", List.of(Map.of("config", exportConfig))), Map.of(), "asset", new HashSet<String>(), sources);
            assertEquals(Set.of("interface-v2"), sources);
        }
    }

    @Test void importingAnExistingChecksumReturnsItsBatchWithoutCreatingAnother() throws Exception {
        try (var f = new Fixture()) {
            currentTable(f, "config_import_package");
            var h = new Harness(f, ConfigImportPackageMapper.class);
            var alteration = Pattern.compile("ALTER TABLE config_import_package[^;]*;", Pattern.DOTALL).matcher(migration("V091__migration_signing_global_setting.sql"));
            assertTrue(alteration.find()); h.jdbc.execute(alteration.group());
            h.jdbc.update("INSERT INTO config_import_package(id,package_no,migration_tag,file_name,checksum,package_data,status) VALUES ('existing','package','tag','file.wfpack','checksum',?,'PUBLISHED')", new byte[]{1});
            var codec = mock(ConfigMigrationPackageCodec.class);
            var decoded = new ConfigMigrationPackageCodec.DecodedPackage("package", "tag", "source", "checksum", "signature", true, Map.of(), List.of());
            when(codec.decode(any())).thenReturn(decoded);
            var target = service(ConfigMigrationPackageService.class, h, Map.of(ConfigMigrationPackageCodec.class, codec,
                    ConfigImportPackageMapper.class, h.mapper(ConfigImportPackageMapper.class)));
            var result = target.importPackage(new MockMultipartFile("file", "file.wfpack", "application/octet-stream", new byte[]{1}), null, null);
            assertEquals("existing", result.get("id")); assertEquals("PUBLISHED", result.get("status"));
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM config_import_package", Integer.class));
        }
    }

    private static void insertAsset(Harness h, String id, String type, String key, int version, String hash, int deleted) {
        h.jdbc.update("INSERT INTO config_migration_asset(id,asset_type,business_key,asset_name,source_history_id,source_version,migration_tag,snapshot_json,content_hash,deleted) VALUES (?,?,?,'name',?,?,'tag','{}',?,?)",
                id, type, key, "history-" + id, version, hash, deleted);
    }

    private static void seedExtensions(Harness h) {
        h.jdbc.update("INSERT INTO ui_extension_definition(id,extension_type,extension_key,display_name,version,revision,deleted) VALUES "
                + "('interface-v1','INTERFACE','shared','old',1,5,0),('interface-v2','INTERFACE','shared','new',2,7,0),"
                + "('deleted','INTERFACE','shared','deleted',3,8,1),('component','FORM','shared','component',9,1,0)");
    }

    /** V088 的 DDL 字符串只解码到随机表，绝不运行迁移中的清理、数据回填或系统表查询。 */
    private static Harness extensionHarness(Fixture f) throws Exception {
        currentTable(f, "ui_extension_definition");
        var h = new Harness(f, UiExtensionDefinitionMapper.class);
        h.jdbc.execute(migration("V021__ui_extension_entity_scope.sql"));
        String migration = migration("V088__flatten_interface_services_into_extensions.sql");
        int start = migration.indexOf("CONCAT(", migration.indexOf("SET @flow_v088_add_interface_columns_sql"));
        String fragments = migration.substring(start, migration.indexOf("\n  ),", start));
        var literals = Pattern.compile("'((?:[^']|'')*)'").matcher(fragments);
        var ddl = new StringBuilder();
        while (literals.find()) ddl.append(literals.group(1).replace("''", "'"));
        assertTrue(ddl.toString().startsWith("ALTER TABLE `ui_extension_definition`")); h.jdbc.execute(ddl.toString());
        return h;
    }

    private static String migration(String file) throws Exception {
        return Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration", file));
    }

    /** 构造真实服务并注入真实 Mapper/方言，只替代与当前查询无关的外部依赖。 */
    private static <T> T service(Class<T> type, Harness h, Map<Class<?>, Object> dependencies) throws Exception {
        var constructor = type.getConstructors()[0];
        Class<?>[] parameters = constructor.getParameterTypes();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i];
            if (dependencies.containsKey(parameter)) args[i] = dependencies.get(parameter);
            else if (parameter == ObjectMapper.class) args[i] = h.json;
            else if (parameter == DatabaseQueryDialect.class) args[i] = DatabaseQueryDialects.forDatabaseId("MYSQL");
            else if (parameter.getSimpleName().equals("ConfigMigrationPackageDocumentSupport")) {
                var documentConstructor = parameter.getDeclaredConstructor(ObjectMapper.class);
                documentConstructor.setAccessible(true); args[i] = documentConstructor.newInstance(h.json);
            } else args[i] = mock(parameter);
        }
        return type.cast(constructor.newInstance(args));
    }
}
