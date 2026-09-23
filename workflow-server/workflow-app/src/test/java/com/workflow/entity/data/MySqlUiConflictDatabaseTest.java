package com.workflow.entity.data;

import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.api.request.EntityFormNodeCreateRequest;
import com.workflow.entity.form.api.request.EntityFormNodePatchRequest;
import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.api.request.UiComponentTemplateSaveRequest;
import com.workflow.entity.ui.application.UiComponentTemplateService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.*;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static com.workflow.entity.data.MySqlRemainingConflictDatabaseTest.table;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/** UI 写入使用生产 Mapper 和事务代理；同步屏障仅固定竞争窗口，所有数据均在随机测试表内。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlUiConflictDatabaseTest {
    @Test void concurrentNodeKeysReturnCommittedOccupantAndRollbackLosingRequests() throws Exception {
        try (var f = new Fixture()) {
            nodeTables(f);
            var h = nodeHarness(f);
            var delegate = h.mapper(EntityFormNodeMapper.class);
            var nodes = mock(EntityFormNodeMapper.class, delegatesTo(delegate));
            var barrier = new CyclicBarrier(6);
            doAnswer(call -> {
                // 所有请求都已完成空快照预检，再竞争唯一约束；不能由前置校验替代本用例。
                barrier.await(10, TimeUnit.SECONDS);
                return delegate.insert((EntityFormNode) call.getArgument(0));
            }).when(nodes).insert(any(EntityFormNode.class));
            var service = nodeService(h, nodes);
            var results = concurrent(6, index -> {
                try {
                    EntityFormNode created = h.tx.execute(status -> {
                        h.jdbc.update("INSERT INTO ui_conflict_effect VALUES (?)", "worker-" + index);
                        return service.create("form", request("node-" + index, "same_key"));
                    });
                    return new Outcome(true, created.getId());
                } catch (RevisionConflictException conflict) {
                    var occupant = assertInstanceOf(EntityFormNode.class, conflict.getCurrentData());
                    assertEquals("same_key", occupant.getNodeKey());
                    return new Outcome(false, occupant.getId());
                }
            });
            assertEquals(1, results.stream().filter(Outcome::created).count());
            assertEquals(1, results.stream().map(Outcome::id).distinct().count());
            assertEquals(1, count(h, "entity_form_node"));
            assertEquals(1, count(h, "ui_conflict_effect"));
            assertEquals(2, h.jdbc.queryForObject("SELECT revision FROM entity_form WHERE id='form'", Integer.class));
        }
    }

    @Test void primaryKeyConflictDoesNotPretendAnotherNodeOwnsTheRequestedKey() throws Exception {
        try (var f = new Fixture()) {
            nodeTables(f); var h = nodeHarness(f);
            var service = nodeService(h, h.mapper(EntityFormNodeMapper.class));
            service.create("form", request("same_id", "old_key"));
            assertThrows(DuplicateKeyException.class, () -> h.tx.execute(status -> {
                h.jdbc.update("INSERT INTO ui_conflict_effect VALUES ('rollback')");
                return service.create("form", request("same_id", "new_key"));
            }));
            assertEquals(0, count(h, "ui_conflict_effect"));
            assertEquals("old_key", h.jdbc.queryForObject("SELECT node_key FROM entity_form_node", String.class));
        }
    }

    @Test void nodeCheckFailureIsNotTranslatedAndTheOuterTransactionRollsBack() throws Exception {
        try (var f = new Fixture()) {
            nodeTables(f); var h = nodeHarness(f);
            f.jdbc.execute("ALTER TABLE " + f.tables.get("entity_form_node")
                    + " ADD CONSTRAINT ui_check_" + f.suffix + " CHECK (node_key <> 'forbidden')");
            var service = nodeService(h, h.mapper(EntityFormNodeMapper.class));
            var failure = assertThrows(DataAccessException.class, () -> h.tx.execute(status -> {
                h.jdbc.update("INSERT INTO ui_conflict_effect VALUES ('rollback')");
                return service.create("form", request("bad", "forbidden"));
            }));
            assertEquals(3819, assertInstanceOf(java.sql.SQLException.class, failure.getMostSpecificCause()).getErrorCode());
            assertEquals(0, count(h, "entity_form_node"));
            assertEquals(0, count(h, "ui_conflict_effect"));
            assertEquals(1, h.jdbc.queryForObject("SELECT revision FROM entity_form", Integer.class));
        }
    }

    @Test void nodePatchAndFormRevisionRemainOwnedByTheOuterTransaction() throws Exception {
        try (var f = new Fixture()) {
            nodeTables(f); var h = nodeHarness(f);
            var service = nodeService(h, h.mapper(EntityFormNodeMapper.class));
            var initial = request("node", "field");
            initial.setNodeType("SECTION"); initial.setProps(Map.of("label", "initial"));
            service.create("form", initial);
            var patch = new EntityFormNodePatchRequest(); patch.setExpectedRevision(1);
            patch.setProps(Map.of("label", "changed"));
            h.tx.executeWithoutResult(status -> {
                assertEquals(2, service.patch("form", "node", patch).getRevision());
                status.setRollbackOnly();
            });
            assertEquals(1, h.mapper(EntityFormNodeMapper.class).selectById("node").getRevision());
            assertEquals(2, h.jdbc.queryForObject("SELECT revision FROM entity_form", Integer.class));
            assertEquals(2, service.patch("form", "node", patch).getRevision());
            assertEquals(3, h.jdbc.queryForObject("SELECT revision FROM entity_form", Integer.class));
        }
    }

    @Test void versionConflictReturnsTemplateAndRollsBackEarlierTemplateEdits() throws Exception {
        try (var f = new Fixture()) {
            templateTables(f); var h = templateHarness(f); var service = templateService(h);
            String id = service.save(templateRequest(null, "Original", "one")).getId();
            // 模拟绕过服务写入的下一版本，强制触发 INSERT 分支；正常服务请求由父模板锁串行。
            h.jdbc.update("INSERT INTO ui_component_template_version "
                    + "(id, template_id, version, snapshot_document, content_hash) VALUES ('other', ?, 2, '{}', 'other')", id);
            var conflict = assertThrows(RevisionConflictException.class,
                    () -> service.save(templateRequest(id, "Changed", "two")));
            assertEquals(id, assertInstanceOf(UiComponentTemplate.class, conflict.getCurrentData()).getId());
            assertEquals("Original", h.mapper(UiComponentTemplateMapper.class).selectById(id).getTemplateName());
            assertEquals(1, h.mapper(UiComponentTemplateMapper.class).selectById(id).getCurrentVersion());
            assertEquals(2, count(h, "ui_component_template_version"));
        }
    }

    @Test void concurrentTemplateVersionsSerializeAndAnOuterRollbackLeavesNoSnapshot() throws Exception {
        try (var f = new Fixture()) {
            templateTables(f); var h = templateHarness(f); var service = templateService(h);
            String id = service.save(templateRequest(null, "Original", "one")).getId();
            var versions = concurrent(6, index -> service.createVersion(id, Map.of("label", "same"), null).getVersion());
            assertEquals(1, versions.stream().distinct().count());
            assertEquals(2, versions.get(0));
            assertEquals(2, count(h, "ui_component_template_version"));
            h.tx.executeWithoutResult(status -> {
                assertEquals(3, service.createVersion(id, Map.of("label", "rolled_back"), null).getVersion());
                status.setRollbackOnly();
            });
            assertEquals(2, count(h, "ui_component_template_version"));
            assertEquals(2, h.mapper(UiComponentTemplateMapper.class).selectById(id).getCurrentVersion());
            assertEquals(3, service.createVersion(id, Map.of("label", "after"), null).getVersion());
        }
    }

    private record Outcome(boolean created, String id) {}

    private static void nodeTables(Fixture f) throws Exception {
        table(f, "entity_form", "V001__business_schema.sql");
        table(f, "entity_form_node", "V001__business_schema.sql");
        f.table("ui_conflict_effect", "id VARCHAR(64) PRIMARY KEY");
        f.jdbc.update("INSERT INTO " + f.tables.get("entity_form")
                + " (id, entity_id, form_key, form_name) VALUES ('form', 'entity', 'main', 'Main')");
    }

    private static void templateTables(Fixture f) throws Exception {
        table(f, "ui_component_template", "V001__business_schema.sql");
        table(f, "ui_component_template_version", "V001__business_schema.sql");
    }

    private static Harness nodeHarness(Fixture f) {
        return new Harness(f, EntityFormMapper.class, EntityFormNodeMapper.class);
    }

    private static Harness templateHarness(Fixture f) {
        return new Harness(f, UiComponentTemplateMapper.class, UiComponentTemplateVersionMapper.class);
    }

    private static EntityFormNodeService nodeService(Harness h, EntityFormNodeMapper nodes) {
        return h.transactional(new EntityFormNodeService(h.mapper(EntityFormMapper.class), nodes,
                mock(EntityRelationMapper.class), mock(UiConfigReleaseMapper.class),
                mock(EntityUiConfigurationPolicy.class), mock(EntityDefinitionMapper.class),
                mock(EntityFieldMapper.class), mock(SystemEntityFieldPolicy.class), mock(UiEventBindingMapper.class),
                new JsonDocumentCodec(h.json), h.attempt));
    }

    private static UiComponentTemplateService templateService(Harness h) {
        return h.transactional(new UiComponentTemplateService(h.mapper(UiComponentTemplateMapper.class),
                h.mapper(UiComponentTemplateVersionMapper.class), new JsonDocumentCodec(h.json),
                mock(UiExtensionDefinitionMapper.class), h.attempt));
    }

    private static EntityFormNodeCreateRequest request(String id, String key) {
        var request = new EntityFormNodeCreateRequest(); request.setId(id); request.setNodeKey(key);
        request.setNodeType("TEXT"); request.setBindingType("NONE"); return request;
    }

    private static UiComponentTemplateSaveRequest templateRequest(String id, String name, String label) {
        var request = new UiComponentTemplateSaveRequest(); request.setId(id); request.setTemplateKey("COMMON");
        request.setTemplateName(name); request.setTemplateType("BUTTON_GROUP");
        request.setSnapshot(Map.of("label", label)); return request;
    }

    private static int count(Harness h, String table) {
        return h.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
