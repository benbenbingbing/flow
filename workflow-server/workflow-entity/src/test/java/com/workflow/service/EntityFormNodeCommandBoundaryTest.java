package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityFormNodeCreateRequest;
import com.workflow.dto.EntityFormNodePatchRequest;
import com.workflow.dto.EntityFormNodeReorderRequest;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单节点命令边界单元测试。
 * <p>被测对象：{@link EntityFormNodeService}。使用 Mockito 构造桩 fixture，
 * 验证关系未完成节点的编辑/排序保护、根节点排序的显式清空、属性补丁禁止跨父改序、
 * 关系子表单的规范化语义、子表单发布归属校验、整包替换的技术锁与历史绑定迁移。
 */
class EntityFormNodeCommandBoundaryTest {

    /**
     * 验证未完成的关系子表单节点禁止编辑（patch）与排序（reorder），且不会触发任何写库。
     */
    @Test
    void incompleteLegacyRelationCannotBeEditedOrReordered() {
        Fixture fixture = fixture();
        EntityFormNode current = node(
                "relation-1",
                "SUB_FORM",
                "RELATION",
                null,
                null,
                1);
        when(fixture.nodeMapper().selectById(current.getId()))
                .thenReturn(current);

        EntityFormNodePatchRequest patch =
                new EntityFormNodePatchRequest();
        patch.setExpectedRevision(1);
        patch.setProps(Map.of("label", "详情"));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().patch(
                        "form-1", current.getId(), patch));

        EntityFormNodeReorderRequest reorder =
                new EntityFormNodeReorderRequest();
        reorder.setExpectedRevision(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().reorder(
                        "form-1", current.getId(), reorder));

        verify(fixture.nodeMapper(), never()).update(isNull(), any());
    }

    /**
     * 验证排序到根节点时使用显式 parent_id=NULL 清空（而非忽略父级），并通过 ArgumentCaptor 断言 SQL。
     */
    @Test
    void reorderToRootUsesExplicitParentClear() {
        Fixture fixture = fixture();
        EntityFormNode current = node(
                "section-1",
                "SECTION",
                "NONE",
                null,
                "parent-1",
                1);
        when(fixture.nodeMapper().selectById(current.getId()))
                .thenReturn(current);
        when(fixture.nodeMapper().findByFormId("form-1"))
                .thenReturn(List.of(current));
        when(fixture.nodeMapper().findSiblings(
                "form-1", current.getId()))
                .thenReturn(List.of());
        when(fixture.nodeMapper().update(isNull(), any()))
                .thenReturn(1);

        EntityFormNodeReorderRequest request =
                new EntityFormNodeReorderRequest();
        request.setExpectedRevision(1);

        assertDoesNotThrow(() ->
                fixture.service().reorder(
                        "form-1", current.getId(), request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<EntityFormNode>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fixture.nodeMapper()).update(isNull(), captor.capture());
        UpdateWrapper<EntityFormNode> update = captor.getValue();
        assertTrue(update.getSqlSet().startsWith("parent_id="));
        assertTrue(update.getParamNameValuePairs().containsKey("MPGENVAL1"));
        assertNull(update.getParamNameValuePairs().get("MPGENVAL1"));
    }

    /**
     * 验证属性补丁在移动父级时禁止同时设置 orderKey（避免隐式排序副作用），违规抛 IllegalArgumentException 且不写库。
     */
    @Test
    void propertyPatchCannotSetOrderWhileMovingParents() {
        Fixture fixture = fixture();
        EntityFormNode current = node(
                "section-1",
                "SECTION",
                "NONE",
                null,
                "parent-1",
                1);
        when(fixture.nodeMapper().selectById(current.getId()))
                .thenReturn(current);

        EntityFormNodePatchRequest request =
                new EntityFormNodePatchRequest();
        request.setExpectedRevision(1);
        request.setParentId("parent-2");
        request.setOrderKey(-1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().patch(
                        "form-1", current.getId(), request));
        verify(fixture.nodeMapper(), never()).update(isNull(), any());
    }

    /**
     * 验证关系绑定子表单按规范化实体语义填充 subFormConfig（childEntityId/refEntityId/relationType/childRefFieldCode），
     * 伪造 childEntityId 的请求被拒绝。
     */
    @Test
    void relationBoundSubFormUsesCanonicalEntitySemantics() {
        Fixture fixture = fixture();
        when(fixture.relationMapper().selectActiveByBindingRef(
                "entity-1", "details_relation"))
                .thenReturn(relation(
                        "details_relation",
                        "entity-2",
                        EntityRelation.RelationType.ONE_TO_ONE));

        EntityFormNodeCreateRequest valid =
                relationCreateRequest(Map.of(
                        "displayMode", "embedded",
                        "layout", "form"));
        EntityFormNode created =
                fixture.service().create("form-1", valid);
        Map<String, Object> props = fixture.codec().readObject(
                created.getPropsDocument(), "测试节点属性");
        Map<String, Object> componentProps =
                map(props.get("componentProps"));
        Map<String, Object> subFormConfig =
                map(componentProps.get("subFormConfig"));
        assertEquals("entity-2", subFormConfig.get("childEntityId"));
        assertEquals("entity-2", subFormConfig.get("refEntityId"));
        assertEquals("ONE_TO_ONE", subFormConfig.get("relationType"));
        assertEquals(
                "parent_id",
                subFormConfig.get("childRefFieldCode"));

        EntityFormNodeCreateRequest forged =
                relationCreateRequest(Map.of(
                        "childEntityId", "entity-evil",
                        "displayMode", "embedded",
                        "layout", "form"));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().create("form-1", forged));
    }

    /**
     * 验证子表单引用的发布版本必须属于关系所指向的子实体，跨实体发布被拒绝且不落库节点。
     */
    @Test
    void childReleaseMustBelongToRelationChildEntity() {
        Fixture fixture = fixture();
        when(fixture.relationMapper().selectActiveByBindingRef(
                "entity-1", "details_relation"))
                .thenReturn(relation(
                        "details_relation",
                        "entity-2",
                        EntityRelation.RelationType.ONE_TO_ONE));
        EntityForm wrongChildForm = form("form-2", "entity-other", 1);
        when(fixture.formMapper().selectById("form-2"))
                .thenReturn(wrongChildForm);
        UiConfigRelease release = new UiConfigRelease();
        release.setId("release-2");
        release.setConfigType("FORM");
        release.setConfigId("form-2");
        release.setVersion(1);
        release.setSnapshotDocument("{\"nodes\":[]}");
        when(fixture.releaseMapper().selectById("release-2"))
                .thenReturn(release);

        EntityFormNodeCreateRequest request =
                relationCreateRequest(Map.of(
                        "displayMode", "embedded",
                        "layout", "form"));
        request.setChildFormId("form-2");
        request.setChildFormReleaseId("release-2");
        request.setChildFormReleaseVersion(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().create("form-1", request));
        verify(fixture.nodeMapper(), never())
                .insert(any(EntityFormNode.class));
    }

    /**
     * 验证整包替换（replaceByDiff）需校验表单 CAS 期望版本：
     * 缺失期望版本、版本不匹配（RevisionConflictException）及伪造 nodeKey 均被拒绝，不写库。
     */
    @Test
    void userWholePackageRequiresFormCasAndKeepsTechnicalLocks() {
        Fixture fixture = fixture();
        EntityForm currentForm = form("form-1", "entity-1", 3);
        when(fixture.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(currentForm);
        EntityFormNode current = node(
                "field-1",
                "FIELD",
                "ENTITY_FIELD",
                "amount",
                null,
                1);
        current.setPropsDocument(fixture.codec().write(
                Map.of(
                        "fieldId", "field-1",
                        "fieldCode", "amount",
                        "fieldType", "DECIMAL",
                        "componentType", "number"),
                "测试字段属性"));
        when(fixture.nodeMapper().findByFormId("form-1"))
                .thenReturn(List.of(current));
        when(fixture.nodeMapper().selectById(current.getId()))
                .thenReturn(current);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().replaceByDiff(
                        "form-1", List.of(current), null));
        assertThrows(
                RevisionConflictException.class,
                () -> fixture.service().replaceByDiff(
                        "form-1", List.of(current), 2));

        EntityFormNode forged = copy(current);
        forged.setNodeKey("renamed_amount");
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().replaceByDiff(
                        "form-1", List.of(forged), 3));
        verify(fixture.nodeMapper(), never()).update(isNull(), any());
    }

    /**
     * 验证系统导入（replaceByDiff 不带期望版本）会迁移不支持的旧绑定：
     * 旧 RELATION 绑定被改写为 NONE，原始属性归档到 legacyPropsDocument 的 inactiveNodeProperties 中。
     */
    @Test
    void systemImportMigratesUnsupportedLegacyBinding() {
        Fixture fixture = fixture();
        when(fixture.nodeMapper().findByFormId("form-1"))
                .thenReturn(List.of());
        EntityFormNode legacy = node(
                "legacy-1",
                "SUB_FORM",
                "RELATION",
                null,
                null,
                1);
        legacy.setPropsDocument(fixture.codec().write(
                Map.of(
                        "fieldCode", "details",
                        "fieldType", "SUB_FORM",
                        "componentType", "sub_form"),
                "测试旧节点属性"));

        assertDoesNotThrow(() ->
                fixture.service().replaceByDiff(
                        "form-1", List.of(legacy)));

        ArgumentCaptor<EntityFormNode> captor =
                ArgumentCaptor.forClass(EntityFormNode.class);
        verify(fixture.nodeMapper()).insert(captor.capture());
        assertEquals("NONE", captor.getValue().getBindingType());
        Map<String, Object> legacyProps = fixture.codec().readObject(
                captor.getValue().getLegacyPropsDocument(),
                "测试历史属性");
        assertTrue(legacyProps.containsKey("inactiveNodeProperties"));
    }

    /** 构造关系子表单创建请求，subFormConfig 为可定制属性。 */
    private EntityFormNodeCreateRequest relationCreateRequest(
            Map<String, Object> subFormConfig) {
        EntityFormNodeCreateRequest request =
                new EntityFormNodeCreateRequest();
        request.setNodeKey("details");
        request.setNodeType("SUB_FORM");
        request.setBindingType("RELATION");
        request.setBindingRef("details_relation");
        request.setProps(Map.of(
                "fieldCode", "details",
                "fieldType", "SUB_FORM",
                "componentType", "sub_form",
                "componentProps", Map.of(
                        "subFormConfig", subFormConfig)));
        return request;
    }

    /** 构造测试桩 Fixture：mock 全部 mapper 与访问策略，预置 form-1 基础数据。 */
    private Fixture fixture() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormNodeMapper nodeMapper =
                mock(EntityFormNodeMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        UiConfigReleaseMapper releaseMapper =
                mock(UiConfigReleaseMapper.class);
        EntityDefinitionAccessPolicy accessPolicy =
                mock(EntityDefinitionAccessPolicy.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        EntityForm form = form("form-1", "entity-1", 1);
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of());
        when(nodeMapper.findSiblings(any(), any()))
                .thenReturn(List.of());
        return new Fixture(
                new EntityFormNodeService(
                        formMapper,
                        nodeMapper,
                        relationMapper,
                        releaseMapper,
                        accessPolicy,
                        codec),
                formMapper,
                nodeMapper,
                relationMapper,
                releaseMapper,
                codec);
    }

    private EntityForm form(
            String id,
            String entityId,
            int revision) {
        EntityForm form = new EntityForm();
        form.setId(id);
        form.setEntityId(entityId);
        form.setRevision(revision);
        return form;
    }

    private EntityRelation relation(
            String relationCode,
            String childEntityId,
            EntityRelation.RelationType relationType) {
        EntityRelation relation = new EntityRelation();
        relation.setRelationCode(relationCode);
        relation.setChildEntityId(childEntityId);
        relation.setChildRefFieldCode("parent_id");
        relation.setRelationType(relationType);
        relation.setEnabled(true);
        relation.setDeleted(0);
        return relation;
    }

    private EntityFormNode node(
            String id,
            String nodeType,
            String bindingType,
            String bindingRef,
            String parentId,
            int revision) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setFormId("form-1");
        node.setParentId(parentId);
        node.setNodeKey("node_" + id);
        node.setNodeType(nodeType);
        node.setBindingType(bindingType);
        node.setBindingRef(bindingRef);
        node.setOrderKey(1_000_000L);
        node.setRevision(revision);
        node.setDeleted(0);
        return node;
    }

    private EntityFormNode copy(EntityFormNode source) {
        EntityFormNode copy = node(
                source.getId(),
                source.getNodeType(),
                source.getBindingType(),
                source.getBindingRef(),
                source.getParentId(),
                source.getRevision());
        copy.setNodeKey(source.getNodeKey());
        copy.setPropsDocument(source.getPropsDocument());
        copy.setRulesDocument(source.getRulesDocument());
        copy.setDataSourceBindingsDocument(
                source.getDataSourceBindingsDocument());
        copy.setLegacyPropsDocument(source.getLegacyPropsDocument());
        copy.setOrderKey(source.getOrderKey());
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /** 测试夹具：聚合被测 service 及其 mock 依赖与编解码器，便于测试内访问。 */
    private record Fixture(
            EntityFormNodeService service,
            EntityFormMapper formMapper,
            EntityFormNodeMapper nodeMapper,
            EntityRelationMapper relationMapper,
            UiConfigReleaseMapper releaseMapper,
            JsonDocumentCodec codec) {
    }
}
