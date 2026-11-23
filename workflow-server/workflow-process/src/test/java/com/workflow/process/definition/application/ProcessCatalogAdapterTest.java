package com.workflow.process.definition.application;

import com.workflow.core.error.ForbiddenException;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/** 流程目录绑定门闩的锁序与发布状态测试。 */
class ProcessCatalogAdapterTest {

    @Test
    void bindingStatesUseStableLockOrderAndKeepDeletedPublishedDependencies() {
        ProcessDefinitionConfigMapper processMapper =
                mock(ProcessDefinitionConfigMapper.class);
        ProcessVersionHistoryMapper historyMapper =
                mock(ProcessVersionHistoryMapper.class);
        ProcessDefinitionConfig active = process(
                "1", "A", 0);
        ProcessDefinitionConfig deleted = process(
                "2", "B", 1);
        when(processMapper.selectAnyByIdForBindingUpdate("1"))
                .thenReturn(active);
        when(processMapper.selectAnyByIdForBindingUpdate("2"))
                .thenReturn(deleted);
        when(historyMapper.countPublishedVersions("2"))
                .thenReturn(2L);
        ProcessCatalogAdapter adapter = new ProcessCatalogAdapter(
                processMapper, historyMapper, mock(CurrentActorPort.class));

        var states = adapter.lockBindingStates(List.of(
                "02", "1", "2"));

        InOrder lockOrder = inOrder(processMapper);
        lockOrder.verify(processMapper)
                .selectAnyByIdForBindingUpdate("1");
        lockOrder.verify(processMapper)
                .selectAnyByIdForBindingUpdate("2");
        assertTrue(states.get("1").available());
        assertFalse(states.get("1").hasPublishedVersion());
        assertFalse(states.get("2").available());
        assertTrue(states.get("2").hasPublishedVersion());
    }

    @Test
    void checksDefinitionViewBeforeQueriesAndReturnsOnlyPublishedKeys() {
        var mapper = mock(ProcessDefinitionConfigMapper.class);
        var authorization = mock(CurrentActorPort.class);
        var adapter = new ProcessCatalogAdapter(mapper, mock(ProcessVersionHistoryMapper.class), authorization);

        assertThrows(ForbiddenException.class, () -> adapter.findPublishedByKey("known"));
        assertThrows(ForbiddenException.class, () -> adapter.pagePublished(null, null));
        verifyNoInteractions(mapper);

        when(authorization.hasPermission("process:definition:view")).thenReturn(true);
        var draft = process("1", "pending", 0);
        var published = process("2", "known", 0);
        published.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(mapper.findByProcessKey("known")).thenReturn(Optional.of(published));
        when(mapper.findByProcessKey("pending")).thenReturn(Optional.of(draft));

        assertEquals("2", adapter.findPublishedByKey("known").orElseThrow().id());
        assertTrue(adapter.findPublishedByKey("pending").isEmpty());
    }

    private ProcessDefinitionConfig process(
            String id,
            String key,
            int deleted) {
        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId(id);
        process.setProcessKey(key);
        process.setProcessName(key);
        process.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        process.setDeleted(deleted);
        return process;
    }
}
