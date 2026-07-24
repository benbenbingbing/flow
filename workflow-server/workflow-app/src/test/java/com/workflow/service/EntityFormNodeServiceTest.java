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

/**
 * 实体表单节点服务测试。
 *
 * <p>被测对象：{@link EntityFormNodeService}，覆盖节点补丁的乐观锁修订冲突、绑定节点身份/绑定变更拒绝、
 * 节点类型属性适用性校验、重排序、差量替换、节点树层级与循环校验、TAB/TAB_SET 结构校验、
 * 跨表单引用循环与层级校验、遗留子表单钉版、节点键冲突的稳定修订冲突等场景。
 */
class EntityFormNodeServiceTest {

    /** 测试过期修订号返回带当前节点的修订冲突：验证抛出 RevisionConflictException */
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

    /** 测试补丁拒绝修改已绑定节点的身份与绑定信息：验证 nodeKey、nodeType、bindingRef 变更均被拒绝 */
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

    /** 测试补丁拒绝不适配节点类型的属性：验证 SECTION 不接受 componentName、TEXT 不接受 dataSourceBindings、FIELD 不接受 childFormId */
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

    /** 测试重排序允许变更已绑定节点的放置位置：验证 parentId 变更被接受并触发 update */
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

    /** 测试差量替换保留已绑定节点的导入变更：验证对绑定关系节点的导入替换不抛异常并触发 update */
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

    /** 测试拒绝第 9 层嵌套：验证 9 层 SECTION 嵌套时校验抛出 IllegalArgumentException */
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

    /** 测试拒绝父引用成环：验证两个节点互为父节点时校验抛出异常 */
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

    /** 测试恰好 8 层嵌套被允许：验证 8 层 SECTION 嵌套校验通过 */
    @Test
    void allowsExactlyEightLevels() {
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        List<EntityFormNode> nodes = nestedSections(nodeMapper, 8);
        when(nodeMapper.findByFormId("form-1")).thenReturn(nodes);

        assertDoesNotThrow(() -> service(nodeMapper).validateTree("form-1"));
    }

    /** 测试补丁拒绝将子树移动到超过 8 层：验证移动后层级超限被拒绝且不触发 update */
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

    /** 测试补丁允许将子树移动到恰好 8 层：验证移动到 n5 下后校验通过并触发 update */
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

    /** 测试拒绝 TAB 节点不在 TAB_SET 内：验证 TAB 直接位于 SECTION 下时校验抛出异常 */
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

    /** 测试拒绝 TAB_SET 直接子节点非 TAB：验证 FIELD 直接位于 TAB_SET 下时校验抛出异常 */
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

    /** 测试允许 TAB_SET 下嵌套 TAB 与字段：验证三层结构校验通过 */
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

    /** 测试拒绝叶子节点包含子节点：验证 FIELD 节点作为 TEXT 节点的子节点时校验抛出异常 */
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

    /** 测试拒绝多层已发布表单引用成环：验证 form-1 -> form-2 -> form-3 -> form-1 的循环引用校验抛出异常 */
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

    /** 测试拒绝引用未发布的表单：验证引用的 release 不存在时校验抛出异常 */
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

    /** 测试拒绝第 9 层已发布表单引用：验证跨表单引用 8 层后再引用一层（共 9 层）校验抛出异常 */
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

    /** 测试恰好 8 层已发布表单引用被允许：验证 7 层引用加上自身共 8 层校验通过 */
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

    /** 测试遗留设计器 refFormId 钉到激活发布并保留遗留属性：验证创建节点时 childFormRelease 等字段被填充且遗留属性被保留 */
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

    /** 测试为 Repeater 节点钉住遗留 publishedFormId：验证创建后 refFormReleaseId/Version 被填充 */
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

    /** 测试接受显式子发布字段且不依赖嵌套 props：验证直接传入 childFormReleaseId/Version 时被正确持久化 */
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

    /** 测试拒绝设计器 release 版本不匹配：验证引用 release-2 的版本号与实际不一致时校验抛出异常 */
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

    /** 测试拒绝 release 属于其他表单：验证引用的 release 配置 ID 与预期表单不一致时校验抛出异常 */
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

    /** 测试拒绝已发布子快照仍使用未钉版的遗留引用：验证引用的 release 仍含旧 refFormId 时校验抛出异常 */
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

    /** 测试重复的激活节点 key 返回稳定的修订冲突：验证冲突节点作为 currentData 返回 */
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

    /** 测试创建时数据库节点键竞态返回稳定修订冲突：验证唯一键冲突被翻译为 RevisionConflictException */
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

    /** 测试补丁时数据库节点键竞态返回稳定修订冲突：验证 update 唯一键冲突被翻译为 RevisionConflictException */
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

    /** 测试无关的数据库完整性失败不被翻译：验证外键约束失败原样抛出而非转为修订冲突 */
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

    /** 测试差量替换按最深层优先删除缺失节点：验证删除顺序为 leaf -> child -> root */
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

    /** 测试差量替换在树校验前钉住未变更的遗留子表单：验证遗留 refFormId 被钉版后替换不抛异常 */
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

    /** 测试差量替换在写入前拒绝成环的删除图：验证互为父子的节点在替换为空时抛出异常 */
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

    /** 装配带节点 Mapper 与默认 release Mapper 的被测服务 */
    private EntityFormNodeService service(EntityFormNodeMapper nodeMapper) {
        return service(nodeMapper, mock(UiConfigReleaseMapper.class));
    }

    /** 装配带节点 Mapper 与 release Mapper 的完整被测服务，预置表单与关系 Mock */
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

    /** 构造指定层数的嵌套 SECTION 节点列表，并注册 selectById 返回 */
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

    /** 构造指定节点类型的节点对象 */
    private EntityFormNode typedNode(
            String id,
            String parentId,
            String nodeType) {
        EntityFormNode node = node(id, parentId, 1);
        node.setNodeType(nodeType);
        return node;
    }

    /** 构造引用指定已发布表单的 SUB_FORM 节点，含钉版 release 字段 */
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

    /** 构造含子表单引用节点的发布快照对象 */
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

    /** 构造含遗留 refFormId 子表单引用的发布快照对象 */
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

    /** 构造不含子表单引用的发布快照对象 */
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

    /** 将对象强转为 Map，抑制未检查转换警告 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /** 获取测试用 JSON 文档编解码器 */
    private JsonDocumentCodec codec() {
        return new JsonDocumentCodec(new ObjectMapper());
    }

    /** 构造带指定 id、父节点与修订号的测试节点对象 */
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
