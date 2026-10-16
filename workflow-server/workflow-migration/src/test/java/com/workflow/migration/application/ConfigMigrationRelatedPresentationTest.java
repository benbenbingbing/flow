package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.migration.infrastructure.archive.WfpackArchive;
import com.workflow.migration.infrastructure.crypto.WfpackIntegrity;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static com.workflow.admin.setting.application.GlobalSettingRegistry.MIGRATION_SIGNING_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 普通关系展示退役后的迁移行为：清理工作副本，保留 PAGE/独立扩展依赖和历史完整性。 */
class ConfigMigrationRelatedPresentationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fullAndPartialPackagesRemoveRetiredComponentsWithoutMutatingSource(boolean full) {
        var codec = codec();
        Map<String, Object> source = snapshot();
        String originalHash = codec.hashSnapshot(source);

        Map<String, Object> selected = codec.selectSnapshot(source, full ? Map.of("full", true)
                : Map.of("full", false, "sections", List.of("forms", "lists")));

        for (String section : List.of("forms", "lists")) {
            Map<String, Object> special = special(firstComposition(selected, section));
            assertFalse(special.containsKey("customComponent"));
            assertEquals("NONE", special.get("mode"));
            assertEquals("ERROR", special.get("failurePolicy"));
        }
        assertEquals(Set.of("MainPage", "Independent"), keys(selected, "extensions", "extensionKey"));
        assertFalse(keys(selected, "dependencies", "key").contains("Retired@3"));
        assertTrue(keys(selected, "dependencies", "key").contains("Independent@2"));
        assertTrue(keys(selected, "dependencies", "key").contains("MainPage"));
        Map<String, Object> independent = maps(maps(selected.get("forms")).get(0)
                .get("viewCompositions")).get(1);
        assertEquals("BOTH", special(independent).get("mode"));
        assertTrue(special(independent).containsKey("interfaceService"));
        assertTrue(special(independent).containsKey("actionServices"));
        assertEquals(originalHash, codec.hashSnapshot(source));
        assertTrue(special(firstComposition(source, "forms")).containsKey("customComponent"));
    }

    @Test
    void pageUsingSameComponentKeepsItsDefinitionAndDependency() {
        Map<String, Object> source = snapshot();
        Map<String, Object> form = maps(source.get("forms")).get(0);
        form.put("customComponent", "Retired");
        form.put("customComponentVersion", 3);
        Map<String, Object> result = codec().selectSnapshot(source, Map.of("full", true));
        assertTrue(keys(result, "extensions", "extensionKey").contains("Retired"));
        assertTrue(keys(result, "dependencies", "key").contains("Retired@3"));
        assertFalse(special(firstComposition(result, "forms")).containsKey("customComponent"));
    }

    @Test
    void sameKeyInAnotherExtensionTypeIsNotRemoved() {
        Map<String, Object> source = snapshot();
        var extensions = new ArrayList<>(maps(source.get("extensions")));
        extensions.add(Map.of("extensionType", "INTERFACE", "extensionKey", "Retired", "version", 3));
        source.put("extensions", extensions);
        Map<String, Object> result = codec().selectSnapshot(source, Map.of("full", true));
        assertEquals(List.of("INTERFACE"), maps(result.get("extensions")).stream()
                .filter(extension -> "Retired".equals(extension.get("extensionKey")))
                .map(extension -> extension.get("extensionType")).toList());
    }

    @Test
    void oldSignedPackageChecksOriginalHashBeforeDroppingRetiredDependencies() throws Exception {
        var codec = codec();
        Map<String, Object> source = snapshot();
        String originalHash = codec.hashSnapshot(source);
        String assetPath = "assets/entities/project.json";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(assetPath, mapper.writeValueAsBytes(source));
        entries.put("manifest.json", mapper.writeValueAsBytes(Map.of(
                "formatVersion", 3, "packageNo", "legacy", "migrationTag", "legacy",
                "assets", List.of(Map.of("assetType", "ENTITY", "businessKey", "project",
                        "assetName", "Project", "sourceVersion", 1, "sourceHash", originalHash,
                        "path", assetPath, "selection", Map.of("full", true))))));
        Map<String, String> checksums = new LinkedHashMap<>();
        entries.forEach((path, bytes) -> checksums.put(path, WfpackIntegrity.sha256(bytes)));
        byte[] checksumBytes = mapper.writeValueAsBytes(checksums);
        entries.put("checksums.json", checksumBytes);
        entries.put("signature.sig", WfpackIntegrity.hmac(checksumBytes, "test-key").getBytes(StandardCharsets.UTF_8));
        byte[] packageBytes = WfpackArchive.zip(entries);

        var decoded = codec.decode(packageBytes);

        assertTrue(decoded.signatureVerified());
        assertFalse(keys(decoded.assets().get(0).snapshot(), "dependencies", "key").contains("Retired@3"));
        assertArrayEquals(mapper.writeValueAsBytes(source), WfpackArchive.unzip(packageBytes).get(assetPath));
        assertEquals(originalHash, codec.hashSnapshot(source));
    }

    @Test
    void portableExportAndImportDoNotResolveRetiredComponentDefinitions() {
        var entityMapper = mock(EntityDefinitionMapper.class);
        var formMapper = mock(EntityFormMapper.class);
        var extensionMapper = mock(UiExtensionDefinitionMapper.class);
        var entity = new EntityDefinition();
        entity.setId("project-id");
        entity.setEntityCode("project");
        var form = new EntityForm();
        form.setId("detail-id");
        form.setFormKey("detail");
        when(entityMapper.findByEntityCode("project")).thenReturn(Optional.of(entity));
        when(formMapper.selectByEntityIdAndFormKey("project-id", "detail")).thenReturn(form);

        var exportService = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(exportService, "objectMapper", mapper);
        ReflectionTestUtils.setField(exportService, "entityMapper", entityMapper);
        ReflectionTestUtils.setField(exportService, "formMapper", formMapper);
        ReflectionTestUtils.setField(exportService, "extensionDefinitionMapper", extensionMapper);
        Map<String, Object> original = composition("ENTITY_RELATION", "Retired", 3);
        Set<String> extensions = new LinkedHashSet<>();
        List<Map<String, Object>> portable = ReflectionTestUtils.invokeMethod(exportService,
                "portableViewCompositions", Map.of("viewCompositions", List.of(original)),
                Map.of(), "project", extensions, new LinkedHashSet<String>());
        assertTrue(extensions.isEmpty());
        assertFalse(special(portable.get(0)).containsKey("customComponent"));
        var dependencies = new ArrayList<Map<String, Object>>();
        ReflectionTestUtils.invokeMethod(exportService, "collectViewCompositionDependencies",
                Map.of("forms", List.of(Map.of("viewCompositions", List.of(original)))), dependencies);
        assertTrue(dependencies.stream().noneMatch(item -> "CUSTOM_COMPONENT".equals(item.get("type"))));

        var importService = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(importService, "entityMapper", entityMapper);
        ReflectionTestUtils.setField(importService, "formMapper", formMapper);
        ReflectionTestUtils.setField(importService, "extensionDefinitionMapper", extensionMapper);
        ReflectionTestUtils.setField(importService, "environmentMappingMapper", mock(ConfigEnvironmentMappingMapper.class));
        Map<String, Object> imported = ReflectionTestUtils.invokeMethod(importService,
                "resolvePortableViewComposition", entity, original, Map.of());
        assertFalse(special(imported).containsKey("customComponent"));
        assertTrue(special(original).containsKey("customComponent"));
        verifyNoInteractions(extensionMapper);
    }

    private ConfigMigrationPackageCodec codec() {
        var settings = mock(GlobalSettingService.class);
        when(settings.readSystemValue(MIGRATION_SIGNING_KEY)).thenReturn(TextNode.valueOf("test-key"));
        return new ConfigMigrationPackageCodec(mapper, settings);
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> independent = composition("SAME_RECORD", "Independent", 2);
        Map<String, Object> special = special(independent);
        special.put("mode", "BOTH");
        special.put("interfaceService", Map.of("extensionCode", "query.api"));
        special.put("actionServices", List.of(Map.of("extensionCode", "action.api")));
        return new LinkedHashMap<>(Map.of(
                "assetType", "ENTITY", "businessKey", "project", "definition", Map.of("entityCode", "project"),
                "forms", List.of(new LinkedHashMap<>(Map.of("formKey", "detail", "customComponent", "MainPage",
                        "customComponentVersion", 1, "viewCompositions", List.of(
                                composition("ENTITY_RELATION", "Retired", 3), independent)))),
                "lists", List.of(Map.of("listKey", "browse", "viewCompositions", List.of(
                        composition("REVERSE_REFERENCE", "Retired", 3)))),
                "extensions", List.of(Map.of("extensionKey", "Retired", "version", 3),
                        Map.of("extensionKey", "MainPage", "version", 1),
                        Map.of("extensionKey", "Independent", "version", 2)),
                "dependencies", List.of(Map.of("type", "CUSTOM_COMPONENT", "key", "Retired@3", "version", 3),
                        Map.of("type", "CUSTOM_COMPONENT", "key", "MainPage"),
                        Map.of("type", "CUSTOM_COMPONENT", "key", "Independent@2", "version", 2))));
    }

    private Map<String, Object> composition(String relation, String name, int version) {
        return Map.of("compositionKey", relation, "anchorType", "OWNER", "config", Map.of(
                "relation", Map.of("type", relation),
                "target", Map.of("entityCode", "project", "contentType", "FORM", "contentKey", "detail"),
                "specialHandling", new LinkedHashMap<>(Map.of("mode", "CUSTOM_COMPONENT", "failurePolicy", "HIDE",
                        "customComponent", Map.of("name", name, "version", version)))));
    }

    private Map<String, Object> firstComposition(Map<String, Object> snapshot, String section) {
        return maps(maps(snapshot.get(section)).get(0).get("viewCompositions")).get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> special(Map<String, Object> composition) {
        return (Map<String, Object>) ((Map<?, ?>) composition.get("config")).get("specialHandling");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) { return (List<Map<String, Object>>) value; }

    private Set<String> keys(Map<String, Object> snapshot, String section, String key) {
        return maps(snapshot.get(section)).stream().map(item -> String.valueOf(item.get(key)))
                .collect(java.util.stream.Collectors.toSet());
    }
}
