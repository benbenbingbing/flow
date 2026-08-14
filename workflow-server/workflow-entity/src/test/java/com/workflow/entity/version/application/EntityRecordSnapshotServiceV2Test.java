package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCaptureV2;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.FrozenValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityRecordSnapshotServiceV2Test {

    @Mock
    private EntityPublishedSnapshotService publishedSnapshotService;
    @Mock
    private EntityFieldOptionMapper optionMapper;
    @Mock
    private EntityStatusMapper statusMapper;
    @Mock
    private SysDictItemService dictItemService;
    @Mock
    private SysUserService userService;
    @Mock
    private SysOrganizationService organizationService;

    private EntityRecordSnapshotService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new EntityRecordSnapshotService(
                publishedSnapshotService,
                optionMapper,
                statusMapper,
                dictItemService,
                userService,
                organizationService,
                objectMapper);
        when(publishedSnapshotService.getLatestByEntityCode("asset"))
                .thenReturn(published("asset-release-1"));
        when(publishedSnapshotService.getLatestByEntityCode("asset_line"))
                .thenReturn(published("line-release-1"));
    }

    @Test
    void capturesOneLayerRowsAndFrozenChineseValuesFromRowData() {
        EntityVersionConfiguration configuration = configuration(false);
        EntityVersionConfiguration.FilterCondition condition =
                new EntityVersionConfiguration.FilterCondition();
        condition.setFieldCode("category");
        condition.setOperator("EQ");
        condition.setValue("HW");
        configuration.getSnapshotScope().getRelations().get(0)
                .getFilter().setConditions(List.of(condition));

        SnapshotCaptureV2 capture = service.captureV2(
                configuration,
                "asset-1",
                aggregate(List.of(
                        row("line-1", "硬盘", "HW"),
                        row("line-2", "服务", "SERVICE"))),
                false);

        assertEquals(1, capture.datasets().size());
        assertEquals("资产明细", capture.datasets().get(0).relationName());
        assertEquals(1, capture.datasets().get(0).rows().size());
        FrozenValue category = capture.datasets().get(0).rows().get(0)
                .values().get("category");
        assertEquals("HW", category.rawValue());
        assertEquals("硬件", category.displayText());
        assertEquals("明细类型",
                configuration.getSnapshotScope().getRelations().get(0)
                        .getFields().get(1).getFieldName());
    }

    @Test
    void fixedFilterMatchesTypedRowValuesAgainstEditorStrings() {
        EntityVersionConfiguration configuration = configuration(false);
        EntityVersionConfiguration.FilterCondition quantity =
                new EntityVersionConfiguration.FilterCondition();
        quantity.setFieldCode("quantity");
        quantity.setOperator("EQ");
        quantity.setValue("2");
        EntityVersionConfiguration.FilterCondition active =
                new EntityVersionConfiguration.FilterCondition();
        active.setFieldCode("active");
        active.setOperator("EQ");
        active.setValue("true");
        EntityVersionConfiguration.FilterCondition category =
                new EntityVersionConfiguration.FilterCondition();
        category.setFieldCode("category");
        category.setOperator("IN");
        category.setValue(List.of("HW", "SERVICE"));
        configuration.getSnapshotScope().getRelations().get(0)
                .getFilter().setConditions(List.of(
                        quantity, active, category));
        Map<String, Object> typedRow = new LinkedHashMap<>(
                row("line-1", "硬盘", "HW"));
        Map<String, Object> typedData = new LinkedHashMap<>(
                (Map<String, Object>) typedRow.get("data"));
        typedData.put("quantity", 2);
        typedData.put("active", 1);
        typedRow.put("data", typedData);

        SnapshotCaptureV2 capture = service.captureV2(
                configuration,
                "asset-1",
                aggregate(List.of(typedRow)),
                false);

        assertEquals(1, capture.datasets().get(0).rows().size());
    }

    @Test
    void ignoresDatabaseRowOrderUnlessTrackOrderIsEnabled() {
        EntityVersionConfiguration unordered = configuration(false);
        Map<String, Object> first = aggregate(List.of(
                row("line-1", "硬盘", "HW"),
                row("line-2", "内存", "HW")));
        Map<String, Object> reversed = aggregate(List.of(
                row("line-2", "内存", "HW"),
                row("line-1", "硬盘", "HW")));

        assertEquals(
                service.captureV2(unordered, "asset-1", first, false)
                        .dataHash(),
                service.captureV2(unordered, "asset-1", reversed, false)
                        .dataHash());

        EntityVersionConfiguration ordered = configuration(true);
        assertNotEquals(
                service.captureV2(ordered, "asset-1", first, false)
                        .dataHash(),
                service.captureV2(ordered, "asset-1", reversed, false)
                        .dataHash());
    }

    @Test
    void childRecordIdentityParticipatesInDataHash() {
        EntityVersionConfiguration configuration = configuration(false);
        Map<String, Object> original = aggregate(List.of(
                row("line-1", "硬盘", "HW")));
        Map<String, Object> replacementWithSameValues = aggregate(List.of(
                row("line-2", "硬盘", "HW")));

        assertNotEquals(
                service.captureV2(configuration, "asset-1", original, false)
                        .dataHash(),
                service.captureV2(
                                configuration,
                                "asset-1",
                                replacementWithSameValues,
                                false)
                        .dataHash());
        assertEquals(
                "asset_line",
                service.captureV2(configuration, "asset-1", original, false)
                        .datasets().get(0).selector().get("childEntityCode"));
    }

    @Test
    void deletionAndLimitsArePartOfStrictCaptureSemantics() {
        EntityVersionConfiguration configuration = configuration(false);
        Map<String, Object> aggregate = aggregate(List.of(
                row("line-1", "硬盘", "HW")));
        assertNotEquals(
                service.captureV2(configuration, "asset-1", aggregate, false)
                        .dataHash(),
                service.captureV2(configuration, "asset-1", aggregate, true)
                        .dataHash());

        configuration.getSnapshotScope().getLimits()
                .setMaxRowsPerRelation(1);
        configuration.getSnapshotScope().getRelations().get(0)
                .setMaxRows(10);
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.captureV2(
                        configuration,
                        "asset-1",
                        aggregate(List.of(
                                row("line-1", "硬盘", "HW"),
                                row("line-2", "内存", "HW"))),
                        false));
        assertEquals("ENTITY_VERSION_SCOPE_LIMIT_EXCEEDED",
                exception.getErrorCode());
    }

    @Test
    void rejectsMultipleRowsForOneToOneRelation() {
        EntityVersionConfiguration configuration = configuration(false);
        configuration.getSnapshotScope().getRelations().get(0)
                .setRelationType("ONE_TO_ONE");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.captureV2(
                        configuration,
                        "asset-1",
                        aggregate(List.of(
                                row("line-1", "硬盘", "HW"),
                                row("line-2", "内存", "HW"))),
                        false));

        assertEquals("ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION",
                exception.getErrorCode());
    }

    @Test
    void rejectsCaptureWhenFrozenEntityReleaseIsStale() {
        when(publishedSnapshotService.getLatestByEntityCode("asset_line"))
                .thenReturn(published("line-release-2"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.captureV2(
                        configuration(false),
                        "asset-1",
                        aggregate(List.of()),
                        false));

        assertEquals("ENTITY_VERSION_SCOPE_STALE", exception.getErrorCode());
    }

    private EntityVersionConfiguration configuration(boolean trackOrder) {
        EntityVersionConfiguration value = new EntityVersionConfiguration();
        value.setEntityCode("asset");
        value.setEntityName("资产");
        value.getDiffPolicy().setTrackOrder(trackOrder);
        value.getSnapshotScope().setScopeHash("scope-1");
        EntityVersionConfiguration.ScopeNode root =
                value.getSnapshotScope().getRoot();
        root.setEntityCode("asset");
        root.setEntityName("资产");
        root.setEntityReleaseId("asset-release-1");
        root.setEntityReleaseVersion(1);
        root.setFields(List.of(field(
                "assetName", "资产名称", "TEXT", Map.of(), 1)));

        EntityVersionConfiguration.RelationScope relation =
                new EntityVersionConfiguration.RelationScope();
        relation.setNodeCode("REL_LINES");
        relation.setRelationCode("asset_lines");
        relation.setRelationName("资产明细");
        relation.setChildEntityCode("asset_line");
        relation.setChildEntityName("资产明细实体");
        relation.setEntityReleaseId("line-release-1");
        relation.setEntityReleaseVersion(1);
        relation.setDataKey("lines");
        relation.setRelationType("ONE_TO_MANY");
        relation.setFields(List.of(
                field("productName", "产品名称", "TEXT", Map.of(), 1),
                field("category", "明细类型", "SELECT",
                        Map.of("HW", "硬件", "SERVICE", "服务"), 2)));
        value.getSnapshotScope().setRelations(List.of(relation));
        return value;
    }

    private EntityVersionConfiguration.FieldPresentation field(
            String code,
            String name,
            String type,
            Map<String, String> labels,
            int order) {
        EntityVersionConfiguration.FieldPresentation value =
                new EntityVersionConfiguration.FieldPresentation();
        value.setFieldCode(code);
        value.setFieldName(name);
        value.setFieldLabel(name);
        value.setFieldType(type);
        value.setOptionLabels(labels);
        value.setSortOrder(order);
        return value;
    }

    private Map<String, Object> aggregate(List<Map<String, Object>> rows) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assetName", "服务器");
        data.put("lines", rows);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", "asset-1");
        value.put("data", data);
        return value;
    }

    private Map<String, Object> row(
            String id,
            String productName,
            String category) {
        return Map.of(
                "id", id,
                "data", Map.of(
                        "productName", productName,
                        "category", category));
    }

    private EntityPublishedSnapshot published(String releaseId) {
        EntityPublishedSnapshot value = new EntityPublishedSnapshot();
        value.setHistoryId(releaseId);
        return value;
    }
}
