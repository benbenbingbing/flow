package com.workflow.service;

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
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFormNodeServiceTest {

    @Test
    void staleRevisionReturnsConflictWithCurrentNode() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode current = node("n1", null, 2);
        when(nodeMapper.selectById("n1")).thenReturn(current);
        EntityFormNodeService service = service(nodeMapper);

        EntityFormNodePatchRequest request = new EntityFormNodePatchRequest();
        request.setExpectedRevision(1);
        request.setNodeKey("changed");

        assertThrows(
                RevisionConflictException.class,
                () -> service.patch("form-1", "n1", request));
    }

    @Test
    void patchRejectsBoundNodeIdentityAndBindingChanges() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode current = node("n1", null, 1);
        current.setBindingType("ENTITY_FIELD");
        current.setBindingRef("name");
        when(nodeMapper.selectById("n1")).thenReturn(current);
        EntityFormNodeService service = service(nodeMapper);

        EntityFormNodePatchRequest nodeKeyRequest =
                new EntityFormNodePatchRequest();
        nodeKeyRequest.setExpectedRevision(1);
        nodeKeyRequest.setNodeKey("renamed");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.patch("form-1", "n1", nodeKeyRequest));

        EntityFormNodePatchRequest nodeTypeRequest =
                new EntityFormNodePatchRequest();
        nodeTypeRequest.setExpectedRevision(1);
        nodeTypeRequest.setNodeType("TEXT");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.patch("form-1", "n1", nodeTypeRequest));

        EntityFormNodePatchRequest bindingRequest =
                new EntityFormNodePatchRequest();
        bindingRequest.setExpectedRevision(1);
        bindingRequest.setBindingRef("code");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.patch("form-1", "n1", bindingRequest));

        verify(nodeMapper, never()).update(isNull(), any());
    }

    @Test
    void patchRejectsPropertiesThatDoNotApplyToNodeType() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode section = typedNode("section", null, "SECTION");
        EntityFormNode text = typedNode("text", null, "TEXT");
        EntityFormNode field = typedNode("field", null, "FIELD");
        when(nodeMapper.selectById("section")).thenReturn(section);
        when(nodeMapper.selectById("text")).thenReturn(text);
        when(nodeMapper.selectById("field")).thenReturn(field);
        EntityFormNodeService service = service(nodeMapper);

        EntityFormNodePatchRequest componentRequest =
                new EntityFormNodePatchRequest();
        componentRequest.setExpectedRevision(1);
        componentRequest.setComponentName("custom-section");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.patch("form-1", "section", componentRequest));

        EntityFormNodePatchRequest dataSourceRequest =
                new EntityFormNodePatchRequest();
        dataSourceRequest.setExpectedRevision(1);
        dataSourceRequest.setDataSourceBindings(Map.of(
                "FIELD_OPTIONS", Map.of("provider", "departments")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.patch("form-1", "text", dataSourceRequest));

        EntityFormNodePatchRequest subFormRequest =
                new EntityFormNodePatchRequest();
        subFormRequest.setExpectedRevision(1);
        subFormRequest.setChildFormId("form-2");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.patch("form-1", "field", subFormRequest));

        verify(nodeMapper, never()).update(isNull(), any());
    }

    @Test
    void reorderAllowsBoundNodePlacementChange() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode current = node("n1", null, 1);
        current.setBindingType("ENTITY_FIELD");
        current.setBindingRef("name");
        when(nodeMapper.selectById("n1")).thenReturn(current);
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);

        EntityFormNodeReorderRequest request =
                new EntityFormNodeReorderRequest();
        request.setExpectedRevision(1);
        request.setParentId(null);

        assertSame(
                current,
                service(nodeMapper).reorder("form-1", "n1", request));
        verify(nodeMapper).update(isNull(), any());
    }

    @Test
    void replaceByDiffPreservesBoundNodeImportChanges() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode current = node("n1", null, 1);
        current.setBindingType("ENTITY_FIELD");
        current.setBindingRef("name");
        EntityFormNode imported = node("n1", null, 1);
        imported.setNodeKey("details");
        imported.setNodeType("SUB_FORM");
        imported.setBindingType("RELATION");
        imported.setBindingRef("details_relation");
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(current), List.of(imported));
        when(nodeMapper.selectById("n1")).thenReturn(current);
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);

        assertDoesNotThrow(
                () -> service(nodeMapper)
                        .replaceByDiff("form-1", List.of(imported)));

        verify(nodeMapper).update(isNull(), any());
    }

    @Test
    void rejectsNinthLevelNesting() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        List<EntityFormNode> nodes = new ArrayList<>();
        String parentId = null;
        for (int index = 1; index <= 9; index++) {
            EntityFormNode node = node("n" + index, parentId, 1);
            node.setNodeType("SECTION");
            nodes.add(node);
            when(nodeMapper.selectById(node.getId())).thenReturn(node);
            parentId = node.getId();
        }
        when(nodeMapper.findByFormId("form-1")).thenReturn(nodes);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void rejectsCircularParentReference() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode first = node("n1", "n2", 1);
        first.setNodeType("SECTION");
        EntityFormNode second = node("n2", "n1", 1);
        second.setNodeType("SECTION");
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of(first, second));
        when(nodeMapper.selectById("n1")).thenReturn(first);
        when(nodeMapper.selectById("n2")).thenReturn(second);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void allowsExactlyEightLevels() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        List<EntityFormNode> nodes = nestedSections(nodeMapper, 8);
        when(nodeMapper.findByFormId("form-1")).thenReturn(nodes);

        assertDoesNotThrow(() -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void patchRejectsMovingSubtreeBeyondEightLevels() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        List<EntityFormNode> nodes = nestedSections(nodeMapper, 6);
        EntityFormNode subtree = typedNode("subtree", null, "SECTION");
        EntityFormNode child = typedNode("subtree-child", "subtree", "SECTION");
        EntityFormNode grandchild =
                typedNode("subtree-grandchild", "subtree-child", "FIELD");
        nodes.add(subtree);
        nodes.add(child);
        nodes.add(grandchild);
        when(nodeMapper.selectById("subtree")).thenReturn(subtree);
        when(nodeMapper.findByFormId("form-1")).thenReturn(nodes);

        EntityFormNodePatchRequest request =
                new EntityFormNodePatchRequest();
        request.setExpectedRevision(1);
        request.setParentId("n6");

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper).patch("form-1", "subtree", request));
        verify(nodeMapper, never()).update(isNull(), any());
    }

    @Test
    void patchAllowsMovingSubtreeToExactlyEightLevels() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        List<EntityFormNode> nodes = nestedSections(nodeMapper, 5);
        EntityFormNode subtree = typedNode("subtree", null, "SECTION");
        EntityFormNode child = typedNode("subtree-child", "subtree", "SECTION");
        EntityFormNode grandchild =
                typedNode("subtree-grandchild", "subtree-child", "FIELD");
        nodes.add(subtree);
        nodes.add(child);
        nodes.add(grandchild);
        when(nodeMapper.selectById("subtree")).thenReturn(subtree);
        when(nodeMapper.findByFormId("form-1")).thenReturn(nodes);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);

        EntityFormNodePatchRequest request =
                new EntityFormNodePatchRequest();
        request.setExpectedRevision(1);
        request.setParentId("n5");

        assertDoesNotThrow(
                () -> service(nodeMapper).patch("form-1", "subtree", request));
        verify(nodeMapper).update(isNull(), any());
    }

    @Test
    void rejectsTabOutsideTabSet() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode section = typedNode("section", null, "SECTION");
        EntityFormNode tab = typedNode("tab", "section", "TAB");
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of(section, tab));
        when(nodeMapper.selectById("section")).thenReturn(section);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void rejectsNonTabDirectChildOfTabSet() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode tabSet = typedNode("tabs", null, "TAB_SET");
        EntityFormNode field = typedNode("field", "tabs", "FIELD");
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of(tabSet, field));
        when(nodeMapper.selectById("tabs")).thenReturn(tabSet);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void allowsTabSetWithTabAndNestedField() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode tabSet = typedNode("tabs", null, "TAB_SET");
        EntityFormNode tab = typedNode("tab", "tabs", "TAB");
        EntityFormNode field = typedNode("field", "tab", "FIELD");
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of(tabSet, tab, field));
        when(nodeMapper.selectById("tabs")).thenReturn(tabSet);
        when(nodeMapper.selectById("tab")).thenReturn(tab);

        assertDoesNotThrow(() -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void rejectsChildUnderLeafNode() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode text = typedNode("text", null, "TEXT");
        EntityFormNode field = typedNode("field", "text", "FIELD");
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of(text, field));
        when(nodeMapper.selectById("text")).thenReturn(text);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper).validateTree("form-1"));
    }

    @Test
    void rejectsMultiLevelPublishedFormCycle() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "release-2",
                        2)));
        when(releaseMapper.selectById("release-2"))
                .thenReturn(release(
                        "release-2",
                        "form-2",
                        2,
                        "form-3",
                        "release-3",
                        4));
        when(releaseMapper.selectById("release-3"))
                .thenReturn(release(
                        "release-3",
                        "form-3",
                        4,
                        "form-1",
                        "release-1",
                        7));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void rejectsReferenceToUnpublishedForm() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "missing-release",
                        1)));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void rejectsNinthPublishedFormLevel() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "release-2",
                        1)));
        for (int index = 2; index <= 8; index++) {
            when(releaseMapper.selectById("release-" + index))
                    .thenReturn(release(
                            "release-" + index,
                            "form-" + index,
                            1,
                            "form-" + (index + 1),
                            "release-" + (index + 1),
                            1));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void allowsExactlyEightPublishedFormLevels() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "release-2",
                        1)));
        for (int index = 2; index < 8; index++) {
            when(releaseMapper.selectById("release-" + index))
                    .thenReturn(release(
                            "release-" + index,
                            "form-" + index,
                            1,
                            "form-" + (index + 1),
                            "release-" + (index + 1),
                            1));
        }
        when(releaseMapper.selectById("release-8"))
                .thenReturn(releaseWithoutReferences(
                        "release-8",
                        "form-8",
                        1));

        assertDoesNotThrow(
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void pinsLegacyDesignerRefFormIdToActiveReleaseAndPreservesLegacyProps() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        UiConfigRelease active = releaseWithoutReferences(
                "release-2",
                "form-2",
                6);
        when(releaseMapper.findActive("FORM", "form-2")).thenReturn(active);
        when(releaseMapper.selectById("release-2")).thenReturn(active);

        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setNodeKey("details");
        request.setNodeType("SUB_FORM");
        request.setBindingType("RELATION");
        request.setBindingRef("details_relation");
        request.setProps(Map.of(
                "componentProps",
                Map.of(
                        "subFormConfig",
                        Map.of(
                                "refFormId", "form-2",
                                "legacyMode", "embedded"))));
        request.setLegacyProps(Map.of("unknownLegacyFlag", true));

        EntityFormNode created =
                service(nodeMapper, releaseMapper).create("form-1", request);
        Map<String, Object> props = codec().readObject(
                created.getPropsDocument(),
                "测试节点属性");
        Map<String, Object> componentProps = map(props.get("componentProps"));
        Map<String, Object> subFormConfig =
                map(componentProps.get("subFormConfig"));

        assertEquals("form-2", props.get("childFormId"));
        assertEquals("release-2", props.get("childFormReleaseId"));
        assertEquals(6, props.get("childFormReleaseVersion"));
        assertEquals("form-2", subFormConfig.get("refFormId"));
        assertEquals("embedded", subFormConfig.get("legacyMode"));
        assertEquals("release-2", subFormConfig.get("childFormReleaseId"));
        assertTrue(codec().readObject(
                created.getLegacyPropsDocument(),
                "测试历史属性").containsKey("unknownLegacyFlag"));
    }

    @Test
    void pinsLegacyPublishedFormIdForRepeater() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        UiConfigRelease active = releaseWithoutReferences(
                "release-2",
                "form-2",
                3);
        when(releaseMapper.findActive("FORM", "form-2")).thenReturn(active);
        when(releaseMapper.selectById("release-2")).thenReturn(active);

        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setNodeKey("lines");
        request.setNodeType("REPEATER");
        request.setBindingType("RELATION");
        request.setBindingRef("lines_relation");
        request.setProps(Map.of("publishedFormId", "form-2"));

        EntityFormNode created =
                service(nodeMapper, releaseMapper).create("form-1", request);
        Map<String, Object> props = codec().readObject(
                created.getPropsDocument(),
                "测试节点属性");

        assertEquals("release-2", props.get("refFormReleaseId"));
        assertEquals(3, props.get("refFormReleaseVersion"));
    }

    @Test
    void acceptsExplicitChildReleaseFieldsWithoutNestedProps() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        UiConfigRelease published = releaseWithoutReferences(
                "release-2",
                "form-2",
                5);
        when(releaseMapper.selectById("release-2")).thenReturn(published);

        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setNodeKey("details");
        request.setNodeType("SUB_FORM");
        request.setBindingType("RELATION");
        request.setBindingRef("details_relation");
        request.setChildFormId("form-2");
        request.setChildFormReleaseId("release-2");
        request.setChildFormReleaseVersion(5);

        EntityFormNode created =
                service(nodeMapper, releaseMapper).create("form-1", request);
        Map<String, Object> props = codec().readObject(
                created.getPropsDocument(),
                "测试节点属性");

        assertEquals("form-2", props.get("childFormId"));
        assertEquals("release-2", props.get("childFormReleaseId"));
        assertEquals(5, props.get("childFormReleaseVersion"));
    }

    @Test
    void rejectsDesignerReleaseVersionMismatch() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "release-2",
                        8)));
        when(releaseMapper.selectById("release-2"))
                .thenReturn(releaseWithoutReferences(
                        "release-2",
                        "form-2",
                        9));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void rejectsReleaseBelongingToAnotherForm() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "release-3",
                        1)));
        when(releaseMapper.selectById("release-3"))
                .thenReturn(releaseWithoutReferences(
                        "release-3",
                        "form-3",
                        1));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void rejectsPublishedChildSnapshotThatStillUsesUnpinnedLegacyReference() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(referenceNode(
                        "root-ref",
                        "form-2",
                        "release-2",
                        1)));
        when(releaseMapper.selectById("release-2"))
                .thenReturn(legacyRelease(
                        "release-2",
                        "form-2",
                        1,
                        "form-3"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper, releaseMapper).validateTree("form-1"));
    }

    @Test
    void duplicateActiveNodeKeyReturnsStableRevisionConflict() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        when(formMapper.selectById("form-1")).thenReturn(form);

        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        when(nodeMapper.selectCount(any())).thenReturn(1L);
        EntityFormNode conflicting = node("conflicting", null, 3);
        conflicting.setNodeKey("duplicate_key");
        when(nodeMapper.findActiveByFormIdAndNodeKey(
                "form-1", "duplicate_key"))
                .thenReturn(conflicting);

        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setNodeKey("duplicate_key");
        request.setNodeType("FIELD");
        request.setBindingType("NONE");

        EntityFormNodeService service = new EntityFormNodeService(
                formMapper,
                nodeMapper,
                mock(EntityRelationMapper.class),
                mock(UiConfigReleaseMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                new JsonDocumentCodec(new ObjectMapper()));

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> service.create("form-1", request));

        assertSame(conflicting, exception.getCurrentData());
    }

    @Test
    void databaseNodeKeyRaceReturnsStableRevisionConflict() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        when(nodeMapper.findSiblings(
                anyString(), nullable(String.class)))
                .thenReturn(List.of());
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.insert(any(EntityFormNode.class))).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate entry for key "
                                + "'uk_entity_form_node_active_key'"));
        EntityFormNode conflicting = node("conflicting", null, 4);
        conflicting.setNodeKey("raced_key");
        when(nodeMapper.findActiveByFormIdAndNodeKey(
                "form-1", "raced_key"))
                .thenReturn(conflicting);

        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setNodeKey("raced_key");
        request.setNodeType("FIELD");
        request.setBindingType("NONE");

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> service(nodeMapper).create("form-1", request));

        assertSame(conflicting, exception.getCurrentData());
    }

    @Test
    void databaseNodeKeyRaceOnPatchReturnsStableRevisionConflict() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode current = node("n1", null, 2);
        when(nodeMapper.selectById("n1")).thenReturn(current);
        when(nodeMapper.findSiblings(
                anyString(), nullable(String.class)))
                .thenReturn(List.of());
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.update(isNull(), any())).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate entry for key "
                                + "'uk_entity_form_node_active_key'"));
        EntityFormNode conflicting = node("conflicting", null, 5);
        conflicting.setNodeKey("node_n1");
        when(nodeMapper.findActiveByFormIdAndNodeKey(
                "form-1", "node_n1"))
                .thenReturn(conflicting);

        EntityFormNodePatchRequest request =
                new EntityFormNodePatchRequest();
        request.setExpectedRevision(2);

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> service(nodeMapper).patch("form-1", "n1", request));

        assertSame(conflicting, exception.getCurrentData());
    }

    @Test
    void unrelatedDatabaseIntegrityFailureIsNotTranslated() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        when(nodeMapper.findSiblings(
                anyString(), nullable(String.class)))
                .thenReturn(List.of());
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        "foreign key constraint failed");
        when(nodeMapper.insert(any(EntityFormNode.class)))
                .thenThrow(databaseFailure);

        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setNodeKey("valid_key");
        request.setNodeType("FIELD");
        request.setBindingType("NONE");

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service(nodeMapper).create("form-1", request));

        assertSame(databaseFailure, thrown);
    }

    @Test
    void replaceByDiffDeletesMissingNodesDeepestFirst() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode root = typedNode("root", null, "SECTION");
        EntityFormNode child = typedNode("child", "root", "SECTION");
        EntityFormNode leaf = typedNode("leaf", "child", "FIELD");
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(root, child, leaf), List.of());
        when(nodeMapper.selectById("root")).thenReturn(root);
        when(nodeMapper.selectById("child")).thenReturn(child);
        when(nodeMapper.selectById("leaf")).thenReturn(leaf);
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);

        service(nodeMapper).replaceByDiff("form-1", List.of());

        InOrder deletionOrder = inOrder(nodeMapper);
        deletionOrder.verify(nodeMapper).selectById("leaf");
        deletionOrder.verify(nodeMapper).selectById("child");
        deletionOrder.verify(nodeMapper).selectById("root");
    }

    @Test
    void replaceByDiffPinsUnchangedLegacySubFormBeforeTreeValidation() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        UiConfigReleaseMapper releaseMapper = mock(UiConfigReleaseMapper.class);
        EntityFormNode legacy = typedNode("legacy", null, "SUB_FORM");
        legacy.setNodeKey("details");
        legacy.setBindingType("RELATION");
        legacy.setBindingRef("details_relation");
        legacy.setPropsDocument(codec().write(
                Map.of(
                        "componentProps",
                        Map.of(
                                "subFormConfig",
                                Map.of("refFormId", "form-2"))),
                "测试旧子表单属性"));
        EntityFormNode pinned = typedNode("legacy", null, "SUB_FORM");
        pinned.setNodeKey("details");
        pinned.setBindingType("RELATION");
        pinned.setBindingRef("details_relation");
        pinned.setPropsDocument(codec().write(
                Map.of(
                        "childFormId", "form-2",
                        "childFormReleaseId", "release-2",
                        "childFormReleaseVersion", 3),
                "测试已固定子表单属性"));
        UiConfigRelease active = releaseWithoutReferences(
                "release-2",
                "form-2",
                3);

        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(legacy), List.of(pinned));
        when(nodeMapper.selectById("legacy")).thenReturn(legacy);
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(releaseMapper.findActive("FORM", "form-2")).thenReturn(active);
        when(releaseMapper.selectById("release-2")).thenReturn(active);

        assertDoesNotThrow(() ->
                service(nodeMapper, releaseMapper)
                        .replaceByDiff("form-1", List.of(legacy)));

        verify(nodeMapper).update(isNull(), any());
    }

    @Test
    void replaceByDiffRejectsCircularDeletionGraphBeforeWriting() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormNode first = typedNode("first", "second", "SECTION");
        EntityFormNode second = typedNode("second", "first", "SECTION");
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(first, second));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(nodeMapper)
                        .replaceByDiff("form-1", List.of()));
    }

    private EntityFormNodeService service(EntityFormNodeMapper nodeMapper) {
        return service(nodeMapper, mock(UiConfigReleaseMapper.class));
    }

    private EntityFormNodeService service(
            EntityFormNodeMapper nodeMapper,
            UiConfigReleaseMapper releaseMapper) {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        when(formMapper.selectById("form-1")).thenReturn(form);
        EntityForm childForm = new EntityForm();
        childForm.setId("form-2");
        childForm.setEntityId("entity-2");
        when(formMapper.selectById("form-2")).thenReturn(childForm);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        when(relationMapper.selectActiveByBindingRef(
                anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String bindingRef = invocation.getArgument(1);
                    EntityRelation relation = new EntityRelation();
                    relation.setRelationCode(bindingRef);
                    relation.setChildEntityId("entity-2");
                    relation.setChildRefFieldCode("parent_id");
                    relation.setRelationType(
                            bindingRef.contains("lines")
                                    ? EntityRelation.RelationType.ONE_TO_MANY
                                    : EntityRelation.RelationType.ONE_TO_ONE);
                    return relation;
                });
        return new EntityFormNodeService(
                formMapper,
                nodeMapper,
                relationMapper,
                releaseMapper,
                mock(EntityDefinitionAccessPolicy.class),
                new JsonDocumentCodec(new ObjectMapper()));
    }

    private List<EntityFormNode> nestedSections(
            EntityFormNodeMapper nodeMapper,
            int levels) {
        List<EntityFormNode> nodes = new ArrayList<>();
        String parentId = null;
        for (int index = 1; index <= levels; index++) {
            EntityFormNode node = typedNode("n" + index, parentId, "SECTION");
            nodes.add(node);
            when(nodeMapper.selectById(node.getId())).thenReturn(node);
            parentId = node.getId();
        }
        return nodes;
    }

    private EntityFormNode typedNode(
            String id,
            String parentId,
            String nodeType) {
        EntityFormNode node = node(id, parentId, 1);
        node.setNodeType(nodeType);
        return node;
    }

    private EntityFormNode referenceNode(
            String id,
            String childFormId,
            String releaseId,
            int releaseVersion) {
        EntityFormNode node = typedNode(id, null, "SUB_FORM");
        Map<String, Object> subFormConfig = new LinkedHashMap<>();
        subFormConfig.put("refFormId", childFormId);
        subFormConfig.put("childFormReleaseId", releaseId);
        subFormConfig.put("childFormReleaseVersion", releaseVersion);
        Map<String, Object> componentProps = new LinkedHashMap<>();
        componentProps.put("subFormConfig", subFormConfig);
        node.setPropsDocument(codec().write(
                Map.of("componentProps", componentProps),
                "测试节点属性"));
        return node;
    }

    private UiConfigRelease release(
            String releaseId,
            String formId,
            int version,
            String referencedFormId,
            String referencedReleaseId,
            int referencedReleaseVersion) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeType", "SUB_FORM");
        node.put("nodeKey", "ref_" + referencedFormId);
        node.put("propsDocument", codec().write(
                Map.of(
                        "componentProps",
                        Map.of(
                                "subFormConfig",
                                Map.of(
                                        "refFormId", referencedFormId,
                                        "childFormReleaseId", referencedReleaseId,
                                        "childFormReleaseVersion",
                                        referencedReleaseVersion))),
                "测试发布节点属性"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodes", List.of(node));
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setConfigType("FORM");
        release.setConfigId(formId);
        release.setVersion(version);
        release.setStatus("INACTIVE");
        release.setSnapshotDocument(
                codec().write(snapshot, "测试发布快照"));
        return release;
    }

    private UiConfigRelease legacyRelease(
            String releaseId,
            String formId,
            int version,
            String legacyRefFormId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeType", "SUB_FORM");
        node.put("nodeKey", "legacy_ref");
        node.put("propsDocument", codec().write(
                Map.of(
                        "componentProps",
                        Map.of(
                                "subFormConfig",
                                Map.of("refFormId", legacyRefFormId))),
                "测试旧发布节点属性"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodes", List.of(node));
        UiConfigRelease release = releaseWithoutReferences(
                releaseId,
                formId,
                version);
        release.setSnapshotDocument(codec().write(snapshot, "测试发布快照"));
        return release;
    }

    private UiConfigRelease releaseWithoutReferences(
            String releaseId,
            String formId,
            int version) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodes", List.of());
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setConfigType("FORM");
        release.setConfigId(formId);
        release.setVersion(version);
        release.setStatus("INACTIVE");
        release.setSnapshotDocument(
                codec().write(snapshot, "测试发布快照"));
        return release;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private JsonDocumentCodec codec() {
        return new JsonDocumentCodec(new ObjectMapper());
    }

    private EntityFormNode node(String id, String parentId, int revision) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setFormId("form-1");
        node.setParentId(parentId);
        node.setNodeKey("node_" + id);
        node.setNodeType("FIELD");
        node.setBindingType("NONE");
        node.setOrderKey(1_000_000L);
        node.setRevision(revision);
        node.setDeleted(0);
        return node;
    }
}
