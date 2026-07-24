package com.workflow.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.service.EntityFormNodeService;
import com.workflow.service.UiConfigReleaseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UI 配置引导启动 Runner 单元测试。
 *
 * <p>被测对象为 {@link UiConfigurationBootstrapRunner}，验证启动时补全缺失的历史表单节点、
 * 初始发布失败不中断启动、孤立 TAB 节点自动修复并发布，以及含用户草稿变更时拒绝自动发布。</p>
 */
class UiConfigurationBootstrapRunnerTest {

    /**
     * 部分迁移后应仅补全缺失的历史表单节点，不重新发布。
     *
     * <p>场景：表单已有 amount 节点，旧字段含 amount 与 remark，
     * 断言 replaceByDiff 被调用且 publish 未被调用。</p>
     */
    @Test
    void fillsOnlyMissingLegacyNodesWhenMigrationWasPartial() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper fieldMapper = mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
        EntityFormNodeService nodeService = mock(EntityFormNodeService.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        UiConfigurationBootstrapRunner runner = new UiConfigurationBootstrapRunner(
                formMapper,
                fieldMapper,
                nodeMapper,
                listMapper,
                nodeService,
                releaseService,
                codec,
                directTransactionExecutor());

        EntityForm form = new EntityForm();
        form.setId("form-1");
        EntityFormNode existing = new EntityFormNode();
        existing.setId("node-1");
        existing.setNodeKey("amount");
        existing.setBindingRef("amount");
        EntityFormField amount = legacyField("field-1", "amount");
        EntityFormField remark = legacyField("field-2", "remark");

        when(formMapper.selectList(null)).thenReturn(List.of(form));
        when(listMapper.selectList(null)).thenReturn(List.of());
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of(existing));
        when(fieldMapper.selectByFormId("form-1")).thenReturn(
                List.of(amount, remark));
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(mock(com.workflow.entity.UiConfigRelease.class));

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(nodeService).replaceByDiff(eq("form-1"), anyList());
        verify(releaseService, never()).publish(
                eq(UiConfigReleaseService.FORM),
                eq("form-1"),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 初始发布创建失败时启动应继续进行，不中断应用。
     *
     * <p>场景：无活动发布版本且 publish 抛出异常，断言 publish 被调用但 run 不抛异常。</p>
     */
    @Test
    void continuesStartupWhenInitialReleaseCannotBeCreated() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper fieldMapper = mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
        EntityFormNodeService nodeService = mock(EntityFormNodeService.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        UiConfigurationBootstrapRunner runner = new UiConfigurationBootstrapRunner(
                formMapper,
                fieldMapper,
                nodeMapper,
                listMapper,
                nodeService,
                releaseService,
                codec,
                directTransactionExecutor());

        EntityForm form = new EntityForm();
        form.setId("form-1");
        when(formMapper.selectList(null)).thenReturn(List.of(form));
        when(listMapper.selectList(null)).thenReturn(List.of());
        when(nodeMapper.findByFormId("form-1")).thenReturn(List.of());
        when(fieldMapper.selectByFormId("form-1")).thenReturn(List.of());
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(null);
        when(releaseService.publish(
                eq(UiConfigReleaseService.FORM),
                eq("form-1"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("invalid snapshot"));

        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(releaseService).publish(
                eq(UiConfigReleaseService.FORM),
                eq("form-1"),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 孤立 TAB 节点修复后应发布新版本(仅含允许的修复差异)。
     *
     * <p>场景：孤立 tab-1 的 parentId 缺失，草稿快照已修正 parentId 为 tab-set-1，
     * 断言 updateById 被调用且修复后 parentId、revision、updatedAt 正确，
     * validateTree 与 publish 被调用。</p>
     */
    @Test
    void repairsOrphanTabAndPublishesWhenDraftOnlyContainsAllowedRepairDifferences() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper fieldMapper = mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
        EntityFormNodeService nodeService = mock(EntityFormNodeService.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        UiConfigurationBootstrapRunner runner = new UiConfigurationBootstrapRunner(
                formMapper,
                fieldMapper,
                nodeMapper,
                listMapper,
                nodeService,
                releaseService,
                new JsonDocumentCodec(new ObjectMapper()),
                directTransactionExecutor());

        EntityForm form = new EntityForm();
        form.setId("form-1");
        EntityFormNode tabSet = formNode(
                "tab-set-1", null, "TAB_SET", 100L, 2);
        EntityFormNode orphanTab = formNode(
                "tab-1", "missing-parent", "TAB", 110L, 3);

        when(formMapper.selectList(null)).thenReturn(List.of(form));
        when(listMapper.selectList(null)).thenReturn(List.of());
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(tabSet, orphanTab));
        when(fieldMapper.selectByFormId("form-1")).thenReturn(List.of());
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(mock(com.workflow.entity.UiConfigRelease.class));
        when(releaseService.activeSnapshot(
                UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(formSnapshot(List.of(
                        snapshotNode(
                                "tab-set-1",
                                null,
                                "TAB_SET",
                                100,
                                2,
                                "2026-07-19T09:00:00",
                                "{}"),
                        snapshotNode(
                                "tab-1",
                                "missing-parent",
                                "TAB",
                                110,
                                3,
                                "2026-07-19T09:00:00",
                                "{\"label\":\"页签\"}"))));
        when(releaseService.draftSnapshot(
                UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(formSnapshot(List.of(
                        snapshotNode(
                                "tab-set-1",
                                null,
                                "TAB_SET",
                                100L,
                                9,
                                "2026-07-20T10:00:00",
                                "{}"),
                        snapshotNode(
                                "tab-1",
                                "tab-set-1",
                                "TAB",
                                110L,
                                4,
                                "2026-07-20T10:00:00",
                                "{\"label\":\"页签\"}"))));

        runner.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<EntityFormNode> repairedNode =
                ArgumentCaptor.forClass(EntityFormNode.class);
        verify(nodeMapper).updateById(repairedNode.capture());
        assertEquals("tab-1", repairedNode.getValue().getId());
        assertEquals("tab-set-1", repairedNode.getValue().getParentId());
        assertEquals(4, repairedNode.getValue().getRevision());
        assertNotNull(repairedNode.getValue().getUpdatedAt());
        verify(nodeService).validateTree("form-1");
        verify(releaseService).publish(
                UiConfigReleaseService.FORM,
                "form-1",
                "升级自动修复历史孤立TAB节点");
    }

    /**
     * 孤立 TAB 修复涉及用户未发布的草稿变更时应失败且不发布。
     *
     * <p>场景：草稿快照中 tab-1 的内容与活动快照不同(用户有新变更)，
     * 断言 validateTree 被调用但 publish 未被调用。</p>
     */
    @Test
    void failsStartupWithoutPublishingWhenOrphanTabRepairWouldIncludeUserDraftChanges() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper fieldMapper = mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
        EntityFormNodeService nodeService = mock(EntityFormNodeService.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        UiConfigurationBootstrapRunner runner = new UiConfigurationBootstrapRunner(
                formMapper,
                fieldMapper,
                nodeMapper,
                listMapper,
                nodeService,
                releaseService,
                new JsonDocumentCodec(new ObjectMapper()),
                directTransactionExecutor());

        EntityForm form = new EntityForm();
        form.setId("form-1");
        EntityFormNode tabSet = formNode(
                "tab-set-1", null, "TAB_SET", 100L, 1);
        EntityFormNode orphanTab = formNode(
                "tab-1", "missing-parent", "TAB", 110L, 1);

        when(formMapper.selectList(null)).thenReturn(List.of(form));
        when(listMapper.selectList(null)).thenReturn(List.of());
        when(nodeMapper.findByFormId("form-1"))
                .thenReturn(List.of(tabSet, orphanTab));
        when(fieldMapper.selectByFormId("form-1")).thenReturn(List.of());
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(mock(com.workflow.entity.UiConfigRelease.class));
        when(releaseService.activeSnapshot(
                UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(formSnapshot(List.of(
                        snapshotNode(
                                "tab-set-1",
                                null,
                                "TAB_SET",
                                100,
                                1,
                                "2026-07-19T09:00:00",
                                "{}"),
                        snapshotNode(
                                "tab-1",
                                "missing-parent",
                                "TAB",
                                110,
                                1,
                                "2026-07-19T09:00:00",
                                "{\"label\":\"原页签\"}"))));
        when(releaseService.draftSnapshot(
                UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(formSnapshot(List.of(
                        snapshotNode(
                                "tab-set-1",
                                null,
                                "TAB_SET",
                                100L,
                                1,
                                "2026-07-20T10:00:00",
                                "{}"),
                        snapshotNode(
                                "tab-1",
                                "tab-set-1",
                                "TAB",
                                110L,
                                2,
                                "2026-07-20T10:00:00",
                                "{\"label\":\"用户未发布的新页签\"}"))));

        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(nodeService).validateTree("form-1");
        verify(releaseService, never()).publish(
                eq(UiConfigReleaseService.FORM),
                eq("form-1"),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 构造直接执行的事务执行器 mock，不启动真实事务。
     *
     * @return 同步执行 Supplier 的事务执行器
     */
    @SuppressWarnings("unchecked")
    private static UiConfigurationBootstrapTransactionExecutor
    directTransactionExecutor() {
        UiConfigurationBootstrapTransactionExecutor executor =
                mock(UiConfigurationBootstrapTransactionExecutor.class);
        when(executor.execute(org.mockito.ArgumentMatchers.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        return executor;
    }

    /**
     * 构造测试用旧版表单字段对象。
     *
     * @param id 字段 ID
     * @param fieldCode 字段编码
     * @return 已填充基础字段的 EntityFormField 实例
     */
    private static EntityFormField legacyField(String id, String fieldCode) {
        EntityFormField field = new EntityFormField();
        field.setId(id);
        field.setFieldId(id);
        field.setFieldCode(fieldCode);
        field.setFieldLabel(fieldCode);
        field.setFieldType("STRING");
        field.setComponentType("INPUT");
        return field;
    }

    /**
     * 构造测试用表单节点对象。
     *
     * @param id 节点 ID
     * @param parentId 父节点 ID
     * @param nodeType 节点类型
     * @param orderKey 排序键
     * @param revision 版本号
     * @return 已填充字段的 EntityFormNode 实例
     */
    private static EntityFormNode formNode(
            String id,
            String parentId,
            String nodeType,
            long orderKey,
            int revision) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setFormId("form-1");
        node.setParentId(parentId);
        node.setNodeKey(id);
        node.setNodeType(nodeType);
        node.setOrderKey(orderKey);
        node.setRevision(revision);
        return node;
    }

    /**
     * 构造表单快照文档，含 schemaVersion、configType、form、nodes 与 legacyFields。
     *
     * @param nodes 节点列表
     * @return 快照文档 Map
     */
    private static Map<String, Object> formSnapshot(
            List<Map<String, Object>> nodes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", UiConfigReleaseService.FORM);
        snapshot.put("form", Map.of("id", "form-1"));
        snapshot.put("nodes", nodes);
        snapshot.put("legacyFields", List.of());
        return snapshot;
    }

    /**
     * 构造快照中的单个节点 Map。
     *
     * @param id 节点 ID
     * @param parentId 父节点 ID
     * @param nodeType 节点类型
     * @param orderKey 排序键
     * @param revision 版本号
     * @param updateTime 更新时间
     * @param propsDocument 属性文档 JSON
     * @return 节点属性 Map
     */
    private static Map<String, Object> snapshotNode(
            String id,
            String parentId,
            String nodeType,
            Number orderKey,
            int revision,
            String updateTime,
            String propsDocument) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("formId", "form-1");
        node.put("parentId", parentId);
        node.put("nodeKey", id);
        node.put("nodeType", nodeType);
        node.put("orderKey", orderKey);
        node.put("revision", revision);
        node.put("updateTime", updateTime);
        node.put("propsDocument", propsDocument);
        return node;
    }
}
