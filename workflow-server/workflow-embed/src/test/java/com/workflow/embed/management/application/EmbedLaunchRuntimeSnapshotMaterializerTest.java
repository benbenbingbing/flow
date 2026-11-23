package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.embed.EmbedPublishedListDependencyPort;
import com.workflow.port.embed.runtime.model.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.port.entity.EntityFormPort;
import com.workflow.embed.domain.EmbedReleaseSnapshot;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedLaunchRuntimeSnapshotMaterializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryEmbedManagementRepository repository =
            new InMemoryEmbedManagementRepository();
    private EmbedLaunchRuntimeSnapshotMaterializer materializer;
    private EmbedPublishedListDependencyPort dependencyPort;
    private EntityFormPort newDataFormRuntimePort;

    @BeforeEach
    void setUp() {
        repository.resolvedResource = resolved("list-release-1", 3L,
                "form-release-1", 5L);
        dependencyPort = target -> new com.workflow.port.embed.EmbedPublishedListDependencyPort.ResolvedList(
                new ListCoordinate(
                        target.entityCode(), target.listKey(), "list-1",
                        target.listReleaseId(), target.listReleaseVersion()),
                List.of());
        newDataFormRuntimePort = mock(EntityFormPort.class);
        rebuildMaterializer();
    }

    private void rebuildMaterializer() {
        materializer = new EmbedLaunchRuntimeSnapshotMaterializer(
                new EmbedViewConfigurationValidator(objectMapper, repository),
                repository,
                objectMapper,
                dependencyPort,
                newDataFormRuntimePort);
    }

    @Test
    void sameViewConfigurationAndActiveReleaseReuseOneSnapshot() {
        EmbedReleaseSnapshot first = materialize();
        EmbedReleaseSnapshot second = materialize();

        assertEquals(first.id(), second.id());
        assertEquals(1, repository.releases.get("view-1").size());
    }

    @Test
    void changedFlowActiveReleaseCreatesANewSnapshot() {
        EmbedReleaseSnapshot first = materialize();
        repository.resolvedResource = resolved("list-release-2", 4L,
                "form-release-2", 6L);

        EmbedReleaseSnapshot second = materialize();

        assertNotEquals(first.id(), second.id());
        assertEquals(2, repository.releases.get("view-1").size());
        assertEquals("list-release-2",
                repository.releases.get("view-1").get(1).listReleaseId());
    }

    @Test
    void targetDefaultFormActiveChangeOnlyAffectsNewMaterialization()
            throws Exception {
        ListCoordinate child = new ListCoordinate(
                "asset", "selectable", "asset-list",
                "asset-list-release-5", 5);
        dependencyPort = target -> {
            boolean root = "work_order".equals(target.entityCode());
            return new com.workflow.port.embed.EmbedPublishedListDependencyPort.ResolvedList(
                    new ListCoordinate(
                            target.entityCode(), target.listKey(),
                            root ? "list-1" : "asset-list",
                            target.listReleaseId(),
                            target.listReleaseVersion()),
                    root ? List.of(child) : List.of());
        };
        AtomicReference<com.workflow.port.entity.EntityFormPort.ResolvedForm> active =
                new AtomicReference<>(new com.workflow.port.entity.EntityFormPort.ResolvedForm(
                        "asset-form", "asset-form-release-1", 1));
        when(newDataFormRuntimePort.resolveNewRecordForm(anyString())).thenAnswer(call ->
                "asset".equals(call.getArgument(0)) ? Optional.ofNullable(active.get()) : Optional.empty());
        rebuildMaterializer();

        EmbedReleaseSnapshot first = materialize();
        String firstConfig = repository.releases.get("view-1")
                .get(0).configJson();
        active.set(new com.workflow.port.entity.EntityFormPort.ResolvedForm(
                "asset-form", "asset-form-release-2", 2));
        EmbedReleaseSnapshot second = materialize();

        assertNotEquals(first.id(), second.id());
        assertTrue(firstConfig.contains("asset-form-release-1"));
        assertFalse(firstConfig.contains("asset-form-release-2"));
        assertTrue(repository.releases.get("view-1").get(1)
                .configJson().contains("asset-form-release-2"));
    }

    @Test
    void noTargetDefaultFormIsAuthoritativelyResolvedWithoutCoordinates()
            throws Exception {
        ListCoordinate child = new ListCoordinate(
                "asset", "selectable", "asset-list",
                "asset-list-release-5", 5);
        dependencyPort = target -> {
            boolean root = "work_order".equals(target.entityCode());
            return new com.workflow.port.embed.EmbedPublishedListDependencyPort.ResolvedList(
                    new ListCoordinate(
                            target.entityCode(), target.listKey(),
                            root ? "list-1" : "asset-list",
                            target.listReleaseId(),
                            target.listReleaseVersion()),
                    root ? List.of(child) : List.of());
        };
        rebuildMaterializer();

        materialize();

        var config = objectMapper.readTree(repository.releases.get("view-1")
                .get(0).configJson());
        var childNode = config.path("nativeListDependencyClosure")
                .path("nodes").findValuesAsText("listConfigId");
        assertTrue(childNode.contains("asset-list"));
        var nodes = config.path("nativeListDependencyClosure").path("nodes");
        var assetNode = java.util.stream.StreamSupport.stream(
                        nodes.spliterator(), false)
                .filter(node -> "asset-list".equals(
                        node.path("list").path("listConfigId").asText()))
                .findFirst().orElseThrow();
        assertTrue(assetNode.path("defaultFormResolved").asBoolean());
        assertTrue(assetNode.path("defaultForm").isNull());
    }

    private EmbedReleaseSnapshot materialize() {
        return materializer.materialize(
                "view-1",
                "LIST",
                currentConfig(),
                "app-1",
                Instant.parse("2026-08-31T01:00:00Z"));
    }

    private static ResolvedResource resolved(
            String listReleaseId,
            long listVersion,
            String formReleaseId,
            long formVersion) {
        return new ResolvedResource(
                "work_order", "supplier_open", "form-1",
                listReleaseId, listVersion, formReleaseId, formVersion,
                List.of("id", "title"), List.of("id", "title"),
                List.of(), List.of(), List.of("view"), true);
    }

    private static String currentConfig() {
        return """
                {
                  "target":{"entityCode":"work_order","listKey":"supplier_open",
                    "defaultFormId":"form-1"},
                  "entryModes":["LIST","VIEW"],
                  "capabilities":["LIST_QUERY","RECORD_VIEW"],
                  "fieldPolicy":{"mode":"EXPLICIT","visible":["id","title"],
                    "queryable":["title"],"writable":[],"returnable":["id"]},
                  "actionPolicy":{"allowed":["view"]},
                  "contextSchema":{},"contextBindings":[],"ui":{}
                }
                """;
    }
}
