package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityFormNodePatchRequest;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormNode;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFormNodeServicePropertyPolicyTest {

    @Test
    void collapseEditAcceptsEmptyRulesAndCleansHistoricalPollution() {
        Fixture fixture = fixture(collapseWithHistoricalRules());
        EntityFormNodePatchRequest request = new EntityFormNodePatchRequest();
        request.setExpectedRevision(1);
        request.setProps(Map.of(
                "label", "折叠面板",
                "defaultExpanded", false,
                "accordion", true));
        request.setRules(Map.of(
                "validation", Map.of(),
                "extension", Map.of()));
        request.setClearFields(Set.of(
                "componentName",
                "componentVersion",
                "snapshotVersion",
                "dataSourceBindings",
                "childFormId",
                "childFormReleaseId",
                "childFormReleaseVersion",
                "templateId",
                "templateVersion",
                "localOverrides",
                "bindingRef"));

        assertDoesNotThrow(() ->
                fixture.service().patch("form-1", "collapse-1", request));
        verify(fixture.nodeMapper()).update(isNull(), any());
    }

    @Test
    void collapseEditRejectsMeaningfulFieldRules() {
        Fixture fixture = fixture(collapseWithHistoricalRules());
        EntityFormNodePatchRequest request = new EntityFormNodePatchRequest();
        request.setExpectedRevision(1);
        request.setRules(Map.of(
                "validation", Map.of("minLength", 2)));

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().patch(
                        "form-1", "collapse-1", request));
        verify(fixture.nodeMapper(), never()).update(isNull(), any());
    }

    @Test
    void technicalIdentityOrderingAndLegacyPropsCannotUseGenericPatch() {
        EntityFormNode current = collapseWithHistoricalRules();

        EntityFormNodePatchRequest nodeKeyRequest =
                new EntityFormNodePatchRequest();
        nodeKeyRequest.setExpectedRevision(1);
        nodeKeyRequest.setNodeKey("changed_key");
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture(current).service().patch(
                        "form-1", current.getId(), nodeKeyRequest));

        EntityFormNodePatchRequest orderRequest =
                new EntityFormNodePatchRequest();
        orderRequest.setExpectedRevision(1);
        orderRequest.setOrderKey(2_000_000L);
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture(current).service().patch(
                        "form-1", current.getId(), orderRequest));

        EntityFormNodePatchRequest legacyRequest =
                new EntityFormNodePatchRequest();
        legacyRequest.setExpectedRevision(1);
        legacyRequest.setLegacyProps(Map.of("unsafe", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture(current).service().patch(
                        "form-1", current.getId(), legacyRequest));
    }

    @Test
    void boundFieldIdentityIsLockedAndComponentCompatibilityIsEnforced() {
        EntityFormNode current = boundAmountField();

        EntityFormNodePatchRequest identityRequest =
                new EntityFormNodePatchRequest();
        identityRequest.setExpectedRevision(1);
        identityRequest.setProps(Map.of(
                "fieldId", "field-1",
                "fieldCode", "other_amount",
                "fieldName", "金额",
                "label", "申请金额",
                "fieldType", "DECIMAL",
                "componentType", "number",
                "gridSpan", 12));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture(current).service().patch(
                        "form-1", current.getId(), identityRequest));

        EntityFormNodePatchRequest incompatibleComponentRequest =
                new EntityFormNodePatchRequest();
        incompatibleComponentRequest.setExpectedRevision(1);
        incompatibleComponentRequest.setProps(Map.of(
                "fieldId", "field-1",
                "fieldCode", "amount",
                "fieldName", "金额",
                "label", "申请金额",
                "fieldType", "DECIMAL",
                "componentType", "input",
                "gridSpan", 12));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture(current).service().patch(
                        "form-1",
                        current.getId(),
                        incompatibleComponentRequest));

        EntityFormNodePatchRequest displayRequest =
                new EntityFormNodePatchRequest();
        displayRequest.setExpectedRevision(1);
        displayRequest.setProps(Map.of(
                "fieldId", "field-1",
                "fieldCode", "amount",
                "fieldName", "金额",
                "label", "审批金额",
                "fieldType", "DECIMAL",
                "componentType", "number",
                "gridSpan", 12,
                "required", true,
                "readonly", false,
                "hidden", false));
        Fixture fixture = fixture(current);
        assertDoesNotThrow(() ->
                fixture.service().patch(
                        "form-1", current.getId(), displayRequest));
        verify(fixture.nodeMapper()).update(isNull(), any());
    }

    private Fixture fixture(EntityFormNode current) {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper =
                mock(UiConfigReleaseMapper.class);
        EntityDefinitionAccessPolicy accessPolicy =
                mock(EntityDefinitionAccessPolicy.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(nodeMapper.selectById(current.getId())).thenReturn(current);
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(current));
        when(nodeMapper.findSiblings("form-1", current.getId()))
                .thenReturn(List.of());
        when(nodeMapper.update(isNull(), any())).thenReturn(1);

        return new Fixture(
                new EntityFormNodeService(
                        formMapper,
                        nodeMapper,
                        mock(EntityRelationMapper.class),
                        releaseMapper,
                        accessPolicy,
                        codec),
                nodeMapper);
    }

    private EntityFormNode collapseWithHistoricalRules() {
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityFormNode node = new EntityFormNode();
        node.setId("collapse-1");
        node.setFormId("form-1");
        node.setNodeKey("collapse_details");
        node.setNodeType("COLLAPSE");
        node.setBindingType("NONE");
        node.setPropsDocument(codec.write(
                Map.of(
                        "label", "折叠面板",
                        "fieldId", "legacy-field",
                        "componentProps", Map.of(
                                "defaultExpanded", false,
                                "accordion", true)),
                "测试节点属性"));
        node.setRulesDocument(codec.write(
                Map.of(
                        "validation", Map.of("minLength", 2),
                        "extension", Map.of()),
                "测试节点规则"));
        node.setOrderKey(1_000_000L);
        node.setRevision(1);
        node.setDeleted(0);
        return node;
    }

    private EntityFormNode boundAmountField() {
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityFormNode node = new EntityFormNode();
        node.setId("field-1");
        node.setFormId("form-1");
        node.setNodeKey("field_amount");
        node.setNodeType("FIELD");
        node.setBindingType("ENTITY_FIELD");
        node.setBindingRef("amount");
        node.setPropsDocument(codec.write(
                Map.of(
                        "fieldId", "field-1",
                        "fieldCode", "amount",
                        "fieldName", "金额",
                        "label", "申请金额",
                        "fieldType", "DECIMAL",
                        "componentType", "number",
                        "gridSpan", 12,
                        "required", false,
                        "readonly", false,
                        "hidden", false),
                "测试字段属性"));
        node.setOrderKey(1_000_000L);
        node.setRevision(1);
        node.setDeleted(0);
        return node;
    }

    private record Fixture(
            EntityFormNodeService service,
            EntityFormNodeMapper nodeMapper) {
    }
}
