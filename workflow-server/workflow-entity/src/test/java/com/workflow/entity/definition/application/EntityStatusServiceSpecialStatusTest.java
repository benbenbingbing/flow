package com.workflow.entity.definition.application;

import com.workflow.contracts.process.port.ProcessCatalogPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityStatusServiceSpecialStatusTest {
    @Test void invalidReplacementNeverDeletesOldConfigurationAndEnabledActionCannotLoseItsTarget() {
        var mapper = mock(EntityStatusMapper.class);
        var access = mock(EntityDefinitionAccessPolicy.class);
        var catalog = mock(ProcessCatalogPort.class);
        var service = new EntityStatusService(mapper, access, catalog);
        var entity = new EntityDefinition(); entity.setEntityCode("expense");
        entity.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW); entity.setProcessDefinitionId("process");
        when(access.requireDynamicByCodeForUpdate("expense")).thenReturn(entity);
        when(catalog.requiredEndStatusCategories("process")).thenReturn(Set.of("WITHDRAWN"));
        EntityStatus first = withdrawn("one"), second = withdrawn("two");
        assertThrows(BusinessConflictException.class,
                () -> service.saveStatusList("expense", List.of(first, second)));
        assertThrows(BusinessConflictException.class,
                () -> service.saveStatusList("expense", List.of()));
        when(mapper.selectById("one")).thenReturn(first);
        when(mapper.findByEntityCode("expense")).thenReturn(List.of(first));
        assertThrows(BusinessConflictException.class, () -> service.deleteStatus("one"));
        verify(mapper, never()).physicalDeleteByEntityCode(anyString());
        verify(mapper, never()).updateById(any(EntityStatus.class));
    }

    private EntityStatus withdrawn(String id) {
        var status = new EntityStatus(); status.setId(id); status.setEntityCode("expense");
        status.setStatusCode("W_" + id); status.setStatusCategory("WITHDRAWN"); return status;
    }
}
