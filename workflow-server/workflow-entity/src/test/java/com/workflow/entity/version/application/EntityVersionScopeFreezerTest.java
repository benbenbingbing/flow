package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityFieldOption;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class EntityVersionScopeFreezerTest {

    @Mock
    private EntityPublishedSnapshotService snapshotService;
    @Mock
    private EntityFieldOptionMapper optionMapper;

    private EntityVersionScopeFreezer freezer;

    @BeforeEach
    void setUp() {
        freezer = new EntityVersionScopeFreezer(
                snapshotService, new ObjectMapper(), optionMapper);
        when(snapshotService.getLatestByEntityCode("asset"))
                .thenReturn(rootSnapshot());
        when(snapshotService.getLatestByEntityCode("asset_line"))
                .thenReturn(childSnapshot());
        when(optionMapper.findByFieldId(anyString()))
                .thenAnswer(invocation -> "field-category".equals(
                        invocation.getArgument(0))
                        ? List.of(option("HW", "硬件")) : List.of());
    }

    @Test
    void freezesOnlyOwnedRelationAndNormalizesPublishedFilterField() {
        EntityVersionConfiguration draft = draft("asset_lines", "data.category");

        EntityVersionConfiguration frozen = freezer.freeze(draft);

        EntityVersionConfiguration.RelationScope relation = frozen
                .getSnapshotScope().getRelations().get(0);
        assertEquals("资产明细", relation.getRelationName());
        assertEquals("lines", relation.getDataKey());
        assertEquals("category", relation.getFilter().getConditions()
                .get(0).getFieldCode());
        assertEquals("硬件", relation.getFields().stream()
                .filter(item -> "category".equals(item.getFieldCode()))
                .findFirst().orElseThrow().getOptionLabels().get("HW"));
        assertTrue(frozen.getSnapshotScope().getScopeHash().length() == 64);

        EntityVersionConfiguration enriched = freezer.enrichDraftOptions(
                draft("asset_lines", "category"));
        assertEquals(List.of("asset_lines"), enriched.getRelationOptions()
                .stream()
                .map(EntityVersionConfiguration.RelationOption::getRelationCode)
                .toList());
        assertFalse(enriched.getRelationOptions().stream()
                .anyMatch(item -> "asset_owner".equals(item.getRelationCode())));
    }

    @Test
    void rejectsUnknownFilterFieldAndAssociationScope() {
        assertThrows(IllegalArgumentException.class,
                () -> freezer.freeze(draft("asset_lines", "typoField")));
        assertThrows(IllegalArgumentException.class,
                () -> freezer.freeze(draft("asset_owner", "status")));
    }

    private EntityVersionConfiguration draft(
            String relationCode,
            String filterField) {
        EntityVersionConfiguration configuration =
                new EntityVersionConfiguration();
        configuration.setEntityCode("asset");
        configuration.setEntityName("资产");
        EntityVersionConfiguration.RelationScope relation =
                new EntityVersionConfiguration.RelationScope();
        relation.setRelationCode(relationCode);
        EntityVersionConfiguration.FilterCondition condition =
                new EntityVersionConfiguration.FilterCondition();
        condition.setFieldCode(filterField);
        condition.setOperator("EQ");
        condition.setValue("HW");
        relation.getFilter().setConditions(List.of(condition));
        configuration.getSnapshotScope().setRelations(List.of(relation));
        return configuration;
    }

    private EntityPublishedSnapshot rootSnapshot() {
        EntityPublishedSnapshot value = new EntityPublishedSnapshot();
        value.setHistoryId("asset-release-1");
        value.setEntityId("asset-id");
        value.setEntityCode("asset");
        value.setEntityName("资产");
        value.setVersion(1);
        value.setFields(List.of(field(
                "field-name", "assetName", "资产名称",
                EntityField.FieldType.TEXT)));
        value.setRelations(List.of(
                relation("asset_lines", "资产明细", "asset_line",
                        "lines", EntityRelation.OwnershipType.COMPOSITION),
                relation("asset_owner", "资产负责人", "user_ref",
                        "owner", EntityRelation.OwnershipType.ASSOCIATION)));
        return value;
    }

    private EntityPublishedSnapshot childSnapshot() {
        EntityPublishedSnapshot value = new EntityPublishedSnapshot();
        value.setHistoryId("line-release-1");
        value.setEntityId("line-id");
        value.setEntityCode("asset_line");
        value.setEntityName("资产明细实体");
        value.setVersion(1);
        value.setFields(List.of(
                field("field-product", "productName", "产品名称",
                        EntityField.FieldType.TEXT),
                field("field-category", "category", "明细类型",
                        EntityField.FieldType.SELECT)));
        value.setRelations(List.of());
        return value;
    }

    private EntityRelation relation(
            String code,
            String name,
            String childCode,
            String dataKey,
            EntityRelation.OwnershipType ownership) {
        EntityRelation value = new EntityRelation();
        value.setRelationCode(code);
        value.setRelationName(name);
        value.setChildEntityCode(childCode);
        value.setDataKey(dataKey);
        value.setChildRefFieldCode("assetId");
        value.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        value.setOwnershipType(ownership);
        value.setEnabled(true);
        return value;
    }

    private EntityField field(
            String id,
            String code,
            String name,
            EntityField.FieldType type) {
        EntityField value = new EntityField();
        value.setId(id);
        value.setFieldCode(code);
        value.setFieldName(name);
        value.setFieldType(type);
        value.setSortOrder(1);
        return value;
    }

    private EntityFieldOption option(String code, String label) {
        EntityFieldOption value = new EntityFieldOption();
        value.setOptionValue(code);
        value.setOptionLabel(label);
        return value;
    }
}
