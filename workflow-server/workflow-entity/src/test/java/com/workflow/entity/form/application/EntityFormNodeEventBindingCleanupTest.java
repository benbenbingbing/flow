package com.workflow.entity.form.application;

import org.springframework.jdbc.core.JdbcTemplate;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcWriteAttempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** 删除节点必须同时切断该字段的草稿执行链，但不能改变其他目标或历史发布。 */
class EntityFormNodeEventBindingCleanupTest {
    private final EntityFormNodeMapper nodes = mock(EntityFormNodeMapper.class);
    private final UiEventBindingMapper bindings = mock(UiEventBindingMapper.class);
    private final UiConfigReleaseMapper releases = mock(UiConfigReleaseMapper.class);
    private final EntityFormNodeService service = new EntityFormNodeService(
            mock(EntityFormMapper.class), nodes, mock(EntityRelationMapper.class), releases,
            mock(EntityUiConfigurationPolicy.class), mock(EntityDefinitionMapper.class),
            mock(EntityFieldMapper.class), mock(SystemEntityFieldPolicy.class), bindings,
            new JsonDocumentCodec(new ObjectMapper()), new JdbcWriteAttempt(new JdbcTemplate(),
                        DatabaseDialects.insert(DatabaseVendor.MYSQL)));

    @Test
    void deletingLastFieldNodeClearsOnlyItsLocalBindings() {
        prepare(node("removed", "FIELD", "user_slot"), List.of());

        service.delete("form-1", "removed", 3);

        var ordered = inOrder(nodes, bindings);
        ordered.verify(nodes).update(isNull(), any());
        ordered.verify(bindings).deleteFormFieldBindings("form-1", Set.of("myUser1"));
        verifyNoInteractions(releases);
    }

    @Test
    void retainsBindingsWhenAnotherHiddenNodeHasTheSameFieldCode() {
        EntityFormNode remaining = node("remaining", "FIELD", "second_slot");
        remaining.setPropsDocument("{\"fieldCode\":\"myUser1\",\"hidden\":true,\"readonly\":true}");
        prepare(node("removed", "FIELD", "first_slot"), List.of(remaining));

        service.delete("form-1", "removed", 3);

        verifyNoInteractions(bindings, releases);
    }

    @Test
    void alreadyDeletedSiblingDoesNotKeepBindingsAlive() {
        EntityFormNode sibling = node("old", "FIELD", "old_slot");
        sibling.setDeleted(1);
        prepare(node("removed", "FIELD", "user_slot"), List.of(sibling));

        service.delete("form-1", "removed", 3);

        verify(bindings).deleteFormFieldBindings("form-1", Set.of("myUser1"));
    }

    @Test
    void deletingLayoutNodeDoesNotDeleteFieldEventsWithTheSameKey() {
        prepare(node("removed", "SECTION", "myUser1"), List.of());

        service.delete("form-1", "removed", 3);

        verifyNoInteractions(bindings, releases);
    }

    @Test
    void failedNodeDeletionDoesNotCleanEvents() {
        prepare(node("removed", "FIELD", "myUser1"), List.of());
        when(nodes.update(isNull(), any())).thenReturn(0);

        assertThrows(RevisionConflictException.class, () -> service.delete("form-1", "removed", 3));

        verifyNoInteractions(bindings, releases);
    }

    private void prepare(EntityFormNode removed, List<EntityFormNode> remaining) {
        when(nodes.selectById("removed")).thenReturn(removed);
        when(nodes.selectCount(any())).thenReturn(0L);
        when(nodes.update(isNull(), any())).thenReturn(1);
        when(nodes.findByFormId("form-1")).thenReturn(remaining);
    }

    private EntityFormNode node(String id, String type, String key) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setFormId("form-1");
        node.setNodeType(type);
        node.setNodeKey(key);
        node.setPropsDocument("{\"fieldCode\":\"myUser1\"}");
        node.setDeleted(0);
        node.setRevision(3);
        return node;
    }
}
