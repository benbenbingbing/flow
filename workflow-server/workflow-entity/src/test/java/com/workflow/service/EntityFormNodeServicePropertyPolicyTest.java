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

/**
 * 表单节点服务属性策略单元测试。
 * <p>被测对象：{@link EntityFormNodeService}。验证通过 patch 命令时，折叠面板节点的规则清洗与历史污染清理、
 * 技术标识/排序/历史属性的写保护、绑定字段身份锁定及组件兼容性强制校验。
 */
class EntityFormNodeServicePropertyPolicyTest {

    /**
     * 验证折叠面板编辑接受空规则并清理历史污染字段属性，最终成功落库更新。
     */
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

    /**
     * 验证折叠面板编辑拒绝含语义的字段校验规则（minLength），且不触发写库。
     */
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

    /**
     * 验证技术标识（nodeKey）、排序（orderKey）、历史属性（legacyProps）不能通过通用 patch 修改，
     * 三类请求均抛 IllegalArgumentException。
     */
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

    /**
     * 验证已绑定字段身份锁定：修改 fieldCode 被拒、不兼容组件被拒；
     * 仅展示属性（label/required/readonly/hidden）的修改被接受并成功落库。
     */
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

    /** 构造测试桩 Fixture：mock mapper 与访问策略，预置当前节点及 form-1 数据。 */
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

    /** 构造携带历史污染属性与规则的折叠面板节点。 */
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

    /** 构造绑定金额字段的 DECIMAL 节点，用于字段身份锁定与组件兼容性验证。 */
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

    /** 测试夹具：聚合被测 service 与节点 mapper 桩。 */
    private record Fixture(
            EntityFormNodeService service,
            EntityFormNodeMapper nodeMapper) {
    }
}
