package com.workflow.entity.form.uniqueness.infrastructure.persistence;

import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository.GateKey;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueValueGateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityFormUniqueValueGateRepositoryTest {

    @Test
    void insertsAndLocksDistinctKeysInStableOrder() {
        EntityFormUniqueValueGateMapper mapper =
                mock(EntityFormUniqueValueGateMapper.class);
        EntityFormUniqueValueGateRepository repository =
                new EntityFormUniqueValueGateRepository(mapper);
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

        InOrder order = inOrder(mapper);
        order.verify(mapper).insertIgnore(
                first.scopeKey(), first.valueHash());
        order.verify(mapper).lockForUpdate(
                first.scopeKey(), first.valueHash());
        order.verify(mapper).insertIgnore(
                second.scopeKey(), second.valueHash());
        order.verify(mapper).lockForUpdate(
                second.scopeKey(), second.valueHash());
    }

    @Test
    void failsClosedWhenGateRowCannotBeLocked() {
        EntityFormUniqueValueGateMapper mapper =
                mock(EntityFormUniqueValueGateMapper.class);
        EntityFormUniqueValueGateRepository repository =
                new EntityFormUniqueValueGateRepository(mapper);
        GateKey key = new GateKey("ENTITY:project:name", "aaa");

        assertThrows(
                IllegalStateException.class,
                () -> repository.lockAll(List.of(key)));
    }
}
