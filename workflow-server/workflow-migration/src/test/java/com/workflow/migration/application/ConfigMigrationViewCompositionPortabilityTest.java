package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 关联内容迁移便携引用和细粒度依赖裁剪测试。 */
class ConfigMigrationViewCompositionPortabilityTest {

    @Test
    void formNodeAnchorIsExportedByStableNodeKey() {
        assertEquals(
                "requirements_tab",
                ConfigMigrationAssetService.portableAnchorNodeKey(
                        "source-node-id",
                        Map.of("source-node-id", "requirements_tab")));
        assertThrows(
                IllegalStateException.class,
                () -> ConfigMigrationAssetService.portableAnchorNodeKey(
                        "foreign-node-id",
                        Map.of("source-node-id", "requirements_tab")));
    }

    @Test
    void fineGrainedFormExportCarriesEmbeddedServiceAndExactComponent() {
        ConfigMigrationPackageCodec codec =
                new ConfigMigrationPackageCodec(new ObjectMapper());
        Map<String, Object> composition = Map.of(
                "compositionKey", "requirements",
                "anchorType", "OWNER",
                "orderKey", 1000,
                "config", Map.of(
                        "target", Map.of(
                                "entityCode", "requirement",
                                "contentType", "LIST",
                                "contentKey", "project_requirements"),
                        "specialHandling", Map.of(
                                "interfaceService", Map.of(
                                        "serviceCode", "requirement_lookup"),
                                "customComponent", Map.of(
                                        "name", "RequirementTimeline",
                                        "version", 3))));
        Map<String, Object> snapshot = Map.ofEntries(
                Map.entry("schemaVersion", 1),
                Map.entry("assetType", ConfigMigrationAssetService.ENTITY),
                Map.entry("businessKey", "project"),
                Map.entry("definition", Map.of("entityCode", "project")),
                Map.entry("forms", List.of(Map.of(
                        "formKey", "project_detail",
                        "viewCompositions", List.of(composition)))),
                Map.entry("lists", List.of()),
                Map.entry("dataSources", List.of(Map.of(
                        "sourceCode", "requirement_lookup"))),
                Map.entry("extensions", List.of(Map.of(
                        "extensionKey", "RequirementTimeline",
                        "version", 3))),
                Map.entry("dependencies", List.of(
                        Map.of("type", "ENTITY", "key", "requirement",
                                "required", true),
                        Map.of("type", "INTERFACE_SERVICE",
                                "key", "requirement_lookup",
                                "required", true),
                        Map.of("type", "CUSTOM_COMPONENT",
                                "key", "RequirementTimeline@3",
                                "version", 3,
                                "required", true))));

        Map<String, Object> selected = codec.selectSnapshot(
                snapshot,
                Map.of(
                        "full", false,
                        "sections", List.of("forms"),
                        "formKeys", List.of("project_detail")));

        assertEquals(1, ((List<?>) selected.get("dataSources")).size());
        assertEquals(1, ((List<?>) selected.get("extensions")).size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dependencies =
                (List<Map<String, Object>>) selected.get("dependencies");
        assertTrue(dependencies.stream().anyMatch(value ->
                "requirement".equals(value.get("key"))));
        assertTrue(dependencies.stream().anyMatch(value ->
                "requirement_lookup".equals(value.get("key"))));
        assertTrue(dependencies.stream().anyMatch(value ->
                "RequirementTimeline@3".equals(value.get("key"))
                        && Integer.valueOf(3).equals(value.get("version"))));
    }

    @Test
    void rollbackOfNewFieldExplicitlyClearsPreviouslyAbsentCompositions() {
        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put("forms", List.of(Map.of("formKey", "project_detail")));
        Map<String, Object> imported = Map.of(
                "forms", List.of(Map.of(
                        "formKey", "project_detail",
                        "viewCompositions", List.of(Map.of(
                                "compositionKey", "requirements")))));

        ConfigMigrationImportApplyService
                .markRemovedViewCompositionsForRollback(rollback, imported);

        @SuppressWarnings("unchecked")
        Map<String, Object> form = ((List<Map<String, Object>>)
                rollback.get("forms")).get(0);
        assertEquals(List.of(), form.get("viewCompositions"));
    }
}
