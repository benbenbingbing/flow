package com.workflow.entity.form.uniqueness.infrastructure.persistence;

import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository.GateKey;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueValueGateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import com.workflow.core.database.JdbcLockedRow;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityFormUniqueValueGateRepositoryTest {

    @Test
    void insertsAndLocksDistinctKeysInStableOrder() {
        EntityFormUniqueValueGateMapper mapper =
                mock(EntityFormUniqueValueGateMapper.class);
        JdbcLockedRow lockedRows = mock(JdbcLockedRow.class);
        EntityFormUniqueValueGateRepository repository =
                new EntityFormUniqueValueGateRepository(mapper, lockedRows);
        GateKey first = new GateKey("ENTITY:project:code", "bbb");
        GateKey second = new GateKey("ENTITY:project:name", "aaa");
        when(mapper.lockForUpdate(
                first.scopeKey(), first.valueHash()))
                .thenReturn(first.valueHash());
        when(mapper.lockForUpdate(
                second.scopeKey(), second.valueHash()))
                .thenReturn(second.valueHash());

        repository.lockAll(List.of(
                second,
                first,
                second));

        InOrder order = inOrder(mapper, lockedRows);
        order.verify(lockedRows).ensureAndLock("entity_form_unique_value_gate",
                Map.of("scope_key", first.scopeKey(), "value_hash", first.valueHash()),
                List.of("scope_key", "value_hash"));
        order.verify(mapper).lockForUpdate(
                first.scopeKey(), first.valueHash());
        order.verify(lockedRows).ensureAndLock("entity_form_unique_value_gate",
                Map.of("scope_key", second.scopeKey(), "value_hash", second.valueHash()),
                List.of("scope_key", "value_hash"));
        order.verify(mapper).lockForUpdate(
                second.scopeKey(), second.valueHash());
    }

    @Test
    void failsClosedWhenGateRowCannotBeLocked() {
        EntityFormUniqueValueGateMapper mapper =
                mock(EntityFormUniqueValueGateMapper.class);
        JdbcLockedRow lockedRows = mock(JdbcLockedRow.class);
        EntityFormUniqueValueGateRepository repository =
                new EntityFormUniqueValueGateRepository(mapper, lockedRows);
        GateKey key = new GateKey("ENTITY:project:name", "aaa");

        assertThrows(
                IllegalStateException.class,
                () -> repository.lockAll(List.of(key)));
    }
}
